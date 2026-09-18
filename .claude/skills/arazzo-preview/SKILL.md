---
name: arazzo-preview
description: Open an Arazzo workflow document (e.g. steps/6_arazzo_workflow_output/arazzo.yaml) in the Arazzo Playground builder (https://arazzo.connethics.com/builder) so its workflow diagram can be seen in the browser. Use when asked to view, preview, visualize, or see an Arazzo workflow/document, or to open it in the builder/playground.
---

# Arazzo document preview

Renders a local Arazzo document as a diagram by loading it into the Arazzo
Playground builder at `https://arazzo.connethics.com/builder`, using browser
automation. This is a viewer, not an editor — the source of truth stays the
local `arazzo.yaml`; nothing here writes back to it.

## Which document

If the user doesn't name a file, use `steps/6_arazzo_workflow_output/arazzo.yaml`
(the only Arazzo document in this project as of the `6_arazzo_workflow`
step — see the `arazzo-workflow` skill if a new one needs to be built
first). If more than one Arazzo document exists in the repo, ask which one.

## Steps

1. Use the `mcp__playwright__*` tools (the `playwright` MCP server). Do not
   use `claude-in-chrome` for this site — the builder's YAML pane is a
   Monaco editor, and Monaco's `role=textbox` div is not a real
   `<textarea>`/`<input>`/`[contenteditable]`, so generic fill/type helpers
   reject it (`Element is not an <input>, <textarea> or [contenteditable]
   element`). The clipboard-paste approach below sidesteps that.
2. Navigate to `https://arazzo.connethics.com/builder` with
   `browser_navigate`.
3. Load the local Arazzo document onto the system clipboard with Bash —
   don't try to type or fill it in:
   `pbcopy < steps/6_arazzo_workflow_output/arazzo.yaml` (swap the path for
   whichever document is in scope). This avoids both the fill-target error
   above and any risk of Monaco's auto-indent mangling YAML if the text
   were instead sent as simulated keystrokes.
4. Take a `browser_snapshot`, click into the YAML editor pane (its line
   content, e.g. the first visible line's text — clicking the outer
   `textbox "Editor content"` ref directly can time out because Monaco's
   `.view-line` overlay intercepts the click), then press
   `ControlOrMeta+a` to select all, then `ControlOrMeta+v` to paste.
5. Snapshot again to confirm the pasted content landed (check the last
   line/line count matches the source file) and that the pane now shows a
   "Modified" badge with **Reset**/**Apply** buttons next to the YAML
   label — the paste alone does not update the diagram.
6. Click **Apply**. Snapshot once more and confirm the diagram/step list
   updated to the new document's workflow (title, step count, and
   operation IDs should match the pasted file) rather than an error state
   — if the page shows a parse error, report the exact error text back
   rather than guessing a fix and re-pasting blind.
7. Leave the tab open and tell the user it's ready to view; don't close it
   for them.

## Notes

- This is one of several viewer options noted in `steps/6_arazzo_workflow.md`
  (Arazzo Playground, the VS Code Arazzo Visualizer extension, Jentic's
  Arazzo UI, API Flows Studio) — this skill automates the first one
  specifically because it needs no install.
- The builder is a third-party site: paste only the Arazzo document itself,
  never unrelated project files or secrets.
- Jentic's Arazzo UI (`https://arazzo-ui.jentic.com/`) is a lighter
  alternative for the same kind of preview: navigate there with
  `mcp__playwright__browser_navigate`, click its "Upload Arazzo document"
  button, and use `browser_file_upload` with the local file's absolute
  path — no clipboard trick needed since it takes a real file input.
- If every `mcp__playwright__*` call fails with `Browser is already in use
  for .../ms-playwright-mcp/mcp-chrome-<id>, use --isolated to run
  multiple instances`, an orphaned automation Chrome from a previous
  session is holding that profile. Find and kill it (it's the automation
  browser, not the user's personal Chrome):
  `ps aux | grep "mcp-chrome-<id>"` then `kill <pid>` on the main process
  (the one with `--remote-debugging-pipe ... about:blank`, no `--type=`
  flag), then retry.
