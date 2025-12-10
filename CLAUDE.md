# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands
- Run server: `sbt "runMain fs2chat.server.ServerApp"`
- Run client: `sbt "runMain fs2chat.client.ClientApp --username <username>"`
- Build deployable: `sbt universal:stage`
- Compile: `sbt compile`
- Format code: `sbt scalafmt`

## Code Style Guidelines
- Scala 3 with FS2 (Functional Streams for Scala)
- Use Scala 3's enum syntax instead of sealed traits for ADTs
- Indentation-based syntax for Scala 3 classes/objects
- Prefer immutable data structures and pure functions
- Type signatures on public methods
- Use fs2 streams for handling I/O and concurrency
- CamelCase for methods and variables, PascalCase for types
- Handle errors through the fs2 ecosystem (Resource, Stream)
- Organize imports by package, no wildcard imports
- Always use scalafmt for formatting (automatic on compile)

## Chat Commands
The application supports the following JLine-enabled slash commands:
- `/help` - Show available commands
- `/users` - List connected users
- `/clear` - Clear terminal screen
- `/msg <user> <message>` - Send private message
- `/quit` - Exit the chat