Using the language server with Vim
==================================

The script in this directory needs to be invoked via your neovim config,
`init.vim`; using the `LanguageClient` plugin. If you're using, vundle the
following should be added to your config:

```viml
Plugin 'autozimu/LanguageClient-neovim'
```

You also need to configure the plugin to activate when it sees scala sources:

```viml
" Required for operations modifying multiple buffers like rename.
set hidden

let g:LanguageClient_serverCommands = {
    \ 'scala': ['dottyLanguageServer']
\ }
```

The language client will invoke the command "dottyLanguageServer", when it sees
a scala file. As such, this file needs to be on your `$PATH`.
