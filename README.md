# IntelliJ plugin for Re:VIEW

[![Build](https://github.com/nowsprinting/intellij-review/actions/workflows/build.yml/badge.svg)](https://github.com/nowsprinting/intellij-review/actions/workflows/build.yml)

<!-- Plugin description -->
Native language support for [Re:VIEW](https://github.com/kmuto/review) (`.re`) files.

## Features

- **Syntax highlighting** — headings, block/inline commands, and a dedicated color settings page
- **Code completion** — block/inline command names, block ID/heading label references, and image file names in image block commands
- **Reference resolution** — chapters (`@<chap>`), block IDs (`@<list>` etc.), headings (`@<hd>` etc.), footnotes/endnotes/bibliography
- **Find Usages** — block IDs, heading labels, and chapter files
- **Rename refactoring** — block IDs, heading labels, and chapter file names (Shift+F6)
- **Structure View** — nested heading hierarchy with a dedicated icon for columns
- **Page layout preview** — a split editor pane drawing estimated PDF pages (line and page breaks, heading sizes, and images) from `texdocumentclass` in `config.yml`, with chapter/heading/list/image/table references filled in.
  The preview is an estimate and does not reproduce the exact rendering, so always build with Re:VIEW to check the actual output.
<!-- Plugin description end -->
