<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Intellij-review Changelog

## [Unreleased]

### Added

- Native language support for Re:VIEW (`.re`) files
    - Lexer, parser, and PSI (headings, block commands, inline commands)
    - Syntax highlighting and a color settings page
    - Code completion for block/inline command names, block ID/heading label references, and image file names in `//image`
    - Reference resolution for chapters (`@<chap>`), block IDs (`@<list>` etc.), headings (`@<hd>` etc.), and footnotes/endnotes/bibliography
    - Find Usages for block IDs, heading labels, and chapter files
    - Rename refactoring for block IDs, heading labels, and chapter file names (Shift+F6)
    - Structure View with nested heading hierarchy and a dedicated icon for columns
