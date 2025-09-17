const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

function findJar() {
  const dir = path.join(__dirname, '..', 'lsp', 'build', 'libs');
  if (!fs.existsSync(dir)) return null;
  const jars = fs.readdirSync(dir).filter(f => f.endsWith('.jar'))
    .map(f => path.join(dir, f));
  if (!jars.length) return null;
  jars.sort((a,b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs);
  return jars[0];
}

const jar = process.argv[2] || findJar();
if (!jar) {
  console.error('No LSP jar found. Build with: ./gradlew :lsp:build');
  process.exit(2);
}

console.log('Starting server jar:', jar);
const srv = spawn('java', ['-jar', jar], { stdio: ['pipe','pipe','pipe'] });

srv.stderr.on('data', d => console.error('[server-stderr]', d.toString()));
srv.on('exit', (code, sig) => console.log('[server] exited', code, sig));

let stdoutBuf = Buffer.alloc(0);
srv.stdout.on('data', chunk => {
  stdoutBuf = Buffer.concat([stdoutBuf, Buffer.from(chunk)]);
  // try parse any complete messages
  while (true) {
    const hdrEnd = stdoutBuf.indexOf('\r\n\r\n');
    if (hdrEnd === -1) break;
    const header = stdoutBuf.slice(0, hdrEnd).toString();
    const m = header.match(/Content-Length:\s*(\d+)/i);
    if (!m) { stdoutBuf = stdoutBuf.slice(hdrEnd + 4); continue; }
    const len = parseInt(m[1], 10);
    const total = hdrEnd + 4 + len;
    if (stdoutBuf.length < total) break; // wait for more
    const body = stdoutBuf.slice(hdrEnd + 4, total).toString();
    try {
      const msg = JSON.parse(body);
      console.log('[server->client]', JSON.stringify(msg, null, 2));
    } catch (e) {
      console.error('Failed parse body', e);
    }
    stdoutBuf = stdoutBuf.slice(total);
  }
});

let id = 1;
function send(obj) {
  const json = JSON.stringify(obj);
  const header = `Content-Length: ${Buffer.byteLength(json, 'utf8')}\r\n\r\n`;
  srv.stdin.write(header + json);
  console.log('[client->server]', JSON.stringify(obj, null, 2));
}

(async () => {
  // basic initialize
  send({
    jsonrpc: '2.0',
    id: id++,
    method: 'initialize',
    params: {
      processId: process.pid,
      rootUri: null,
      capabilities: {},
      trace: 'off'
    }
  });

  // wait a bit to receive server responses
  await new Promise(r => setTimeout(r, 1200));

  // send initialized notification
  send({ jsonrpc: '2.0', method: 'initialized', params: {} });

  // optionally open a document to trigger diagnostics
  send({
    jsonrpc: '2.0',
    method: 'textDocument/didOpen',
    params: {
      textDocument: {
        uri: 'file:///example.ksql',
        languageId: 'kafkasql',
        version: 1,
        text: 'USE CONTEXT com;'
      }
    }
  });

  await new Promise(r => setTimeout(r, 1000));

  // graceful shutdown
  send({ jsonrpc: '2.0', id: id++, method: 'shutdown', params: null });
  await new Promise(r => setTimeout(r, 400));
  send({ jsonrpc: '2.0', method: 'exit', params: {} });

  // give server time to exit cleanly
  await new Promise(r => setTimeout(r, 300));
  srv.kill();
})();