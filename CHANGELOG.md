Change Log
==========

All notable changes to this project will be documented in this file. This
change log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/).


## [Unreleased]

...

## [0.2.0] - 2023-05-08

### Added
- Each row read from the parser includes `:line` and `:column` metadata
  providing the position in the input where the row occurred.
- `zip-headers` preserves metadata on incoming rows in the returned records.


## [0.1.1] - 2022-09-27

### Changed
- Compile with Java 1.8 for compatibility with older code.

### Fixed
- Eliminated a reflection warning in the write path.


## 0.1.0 - 2022-08-17

Initial project release.


[Unreleased]: https://github.com/amperity/separator/compare/0.2.0...HEAD
[0.2.0]: https://github.com/amperity/separator/compare/0.1.1...0.2.0
[0.1.1]: https://github.com/amperity/separator/compare/0.1.0...0.1.1
