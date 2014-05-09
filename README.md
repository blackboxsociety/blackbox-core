Black Box Core
==============

Black box core consists of a light weight event loop built directly on top of
NIO. It also comes with TCP client and server data structures which leverage
the event loop to perform functionally pure IO on top of it via scalaz's
`Task[A]`
