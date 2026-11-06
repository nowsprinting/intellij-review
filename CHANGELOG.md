<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Intellij-review Changelog

## [Unreleased]

### Added

- Native language support for Re:VIEW (`.re`) files
    - Lexer, parser, and PSI (headings, block commands, inline commands)
    - Syntax highlighting and a color settings page
    - Code completion for block/inline command names (including user-defined inline commands in `review-ext.rb`), block ID/heading label references, and image file names in image block commands
    - Error highlighting for syntax errors, unclosed blocks, unknown inline commands (taking `review-ext.rb` definitions into account), and unresolved references
    - Warning highlighting for unreferenced footnotes, footnotes referenced by multiple `@<fn>`, IDs shared among list/image/table blocks, and URLs not wrapped in `@<href>`
    - Reference resolution for chapters (`@<chap>`), block IDs (`@<list>` etc.), headings (`@<hd>` etc.), and footnotes/endnotes/bibliography
    - Find Usages for block IDs, heading labels, and chapter files
    - Rename refactoring for block IDs, heading labels, and chapter file names (Shift+F6)
    - Structure View with nested heading hierarchy and a dedicated icon for columns
