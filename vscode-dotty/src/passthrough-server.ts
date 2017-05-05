'use strict';

import {
	IPCMessageReader, IPCMessageWriter,
	createConnection, IConnection, TextDocumentSyncKind,
	TextDocuments, TextDocument, Diagnostic, DiagnosticSeverity,
	InitializeParams, InitializeResult, TextDocumentPositionParams,
	CompletionItem, CompletionItemKind
} from 'vscode-languageserver';

import * as waitForPort from 'wait-for-port';

import * as net from 'net';

let argv = process.argv.slice(2)
let port = argv.shift()

let client = new net.Socket()
client.setEncoding('utf8')
process.stdout.setEncoding('utf8')
process.stdin.setEncoding('utf8')

client.on('data', (data) => {
  process.stdout.write(data.toString())
})
process.stdin.on('readable', () => {
  let chunk = process.stdin.read();
  if (chunk !== null) {
    client.write(chunk)
  }
})


waitForPort('localhost', port, (err) => {
  if (err)
    throw new Error(err)

  client.connect(port)
})
