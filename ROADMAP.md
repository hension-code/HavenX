# Haven Roadmap

## Completed

- [x] **Paste into terminal** — Paste button on keyboard toolbar and selection toolbar. Clipboard text sent as terminal input; selection cleared on paste.
- [x] **Import existing SSH keys** — Allow importing private keys (PEM/OpenSSH/PuTTY PPK format) from device storage, with passphrase support.
- [x] **Background connection notification** — Persistent Android notification while SSH sessions are active, so users know there's an open connection.
- [x] **OSC sequence support** — OSC 8 hyperlinks (clickable links in terminal output), OSC 9/777 notifications (toast foreground, Android notification background), OSC 7 working directory tracking.
- [x] **Bracket paste mode** — Wrap pasted text in `ESC[200~`/`ESC[201~` when DECSET 2004 is enabled, preventing accidental execution of multi-line paste.
- [x] **Highlighter-style text selection** — Long-press and drag to extend selection like a highlighter pen, with edge-scroll for selecting beyond the visible screen. Mutually exclusive gesture handling for tab swipe, scroll, and selection.
- [x] **Terminal rendering fix** — Post emulator writes to main thread to prevent concurrent native access during resize, fixing animation scroll corruption.
- [x] **Keyboard toolbar customization** — Configurable keyboard toolbar with JSON layout support: smaller keys, more keys per row, user-editable layout.
- [x] **Network discovery** — Automatic LAN discovery of SSH hosts via mDNS/broadcast, shown in the connection creation dialog.
- [x] **Port forwarding** — Local (`-L`) and remote (`-R`) SSH port forwarding with visual flow diagrams showing tunnel direction. Rules persist across sessions, auto-activate on connect, restore on reconnect. Live add/edit/remove on active sessions with port validation.
- [x] **ProxyJump / multi-hop tunneling** — `ssh -J` style jump hosts via JSch direct-tcpip channels. Jump host selector in connection editor, tree view of dependencies, cascade disconnect, and terminal access on the jump host with tmux/screen session flow.
- [x] **Backup & restore** — Encrypted export/import of SSH keys, connections, port forward rules, known hosts, and settings. AES-256-GCM with PBKDF2 key derivation, accessible from Settings.

## Near-term

- [ ] **SFTP integration with CWD** — Use OSC 7 working directory to open SFTP browser at the current remote path.

## Medium-term

- [ ] **Agent forwarding** — SSH agent forwarding for key-based authentication to hop hosts.
- [ ] **Snippet/command library** — Save and recall frequently used commands.
- [ ] **Connection groups/folders** — Organize saved connections by project or environment.

## Longer-term

- [ ] **Mosh support** — UDP-based mobile shell for unreliable network connections.
