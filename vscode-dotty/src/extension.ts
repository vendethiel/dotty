'use strict';

import * as fs from 'fs';
import * as path from 'path';
import { spawn } from 'child_process';

import { commands, workspace, Disposable, ExtensionContext, Uri } from 'vscode';
import { Executable, LanguageClient, LanguageClientOptions, SettingMonitor, ServerOptions, TransportKind } from 'vscode-languageclient';
import * as lc from 'vscode-languageclient';
import * as vscode from 'vscode';

// Keep in sync with IDEConfig.java
interface IDEConfig {
  id: string,
  scalaVersion: string,
  sources: string[],
  scalacArgs: string[],
  depCp: string[],
  target: string
}

let extensionContext: ExtensionContext
let outputChannel: vscode.OutputChannel

export function activate(context: ExtensionContext) {
  extensionContext = context
  outputChannel = vscode.window.createOutputChannel('Dotty Language Server');

  outputChannel.show();

  let configFile = `${vscode.workspace.rootPath}/.dotty-ide.json`
  fs.readFile(configFile, (err, data) => {
    if (err) {
      outputChannel.append(`Unable to parse ${configFile}`)
      throw err
    }
    let config: IDEConfig[] = JSON.parse(data.toString())
    // When different versions of dotty are used by subprojects, choose the latest one.
    let version = config.map(x => x.scalaVersion).sort().pop()

    if (process.env['DLS_PORT'] !== undefined) {
      run({
        module: context.asAbsolutePath('out/src/passthrough-server.js'),
        args: [process.env['DLS_PORT']]
      })
    } else {
      fetchAndRun(version)
    }
  })
}

function fetchAndRun(version: String) {
  let coursierPath = path.join(extensionContext.extensionPath, './coursier');

  let coursierProc =
    spawn("java", [
      "-jar", coursierPath,
      "fetch",
      "-p",
      "-r", "ivy2Local",
      "-r", "ivy2Cache",
      "-r", "sonatype:snapshots",
      "-r", "typesafe:ivy-releases",
      "ch.epfl.lamp:dotty-language-server_0.1:" + version
    ])

  let classPath = ""

  coursierProc.stdout.on('data', (data) => {
    classPath += data.toString()
  })
  coursierProc.stderr.on('data', (data) => {
    outputChannel.append(data.toString())
  })

  coursierProc.on('close', (code) => {
    if (code != 0) {
      outputChannel.appendLine("Fetching the language server failed.")
      return
    }

    run({
      command: "java",
      args: ["-cp", classPath, "dotty.tools.dotc.interactive.Main", "-stdio"]
    })
  })
}

function run(serverOptions: ServerOptions) {
  let clientOptions: LanguageClientOptions = {
    documentSelector: ['scala'],
    synchronize: {
      configurationSection: 'dotty'
    }
  }

  outputChannel.dispose()

  let client = new LanguageClient('dotty', 'Dotty Language Server', serverOptions, clientOptions);

  // Push the disposable to the context's subscriptions so that the
  // client can be deactivated on extension deactivation
  extensionContext.subscriptions.push(client.start());

  commands.registerCommand("dotty.fix", (uri: string, range: lc.Range, replacement: string) => {
    let edit = new vscode.WorkspaceEdit();
    edit.replace(Uri.parse(uri), client.protocol2CodeConverter.asRange(range), replacement);
    return vscode.workspace.applyEdit(edit);
  });
}
