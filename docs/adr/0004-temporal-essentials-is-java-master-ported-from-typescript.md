# Temporal Essentials is `decks/java-master/`, ported from the TypeScript Deck

Temporal Essentials, the Foundations Deck that holds every Segment, lives at
`decks/java-master/` and is titled *Temporal Essentials: Java*. Its source is
the TypeScript Deck (`temporalio/temporal-devdays-ts`, `decks/typescript-master/`
at `a61bcf9`), not the Python one, because the TypeScript Deck is the newest
and most complete: it carries every Python Segment plus Start vs. Execute,
Clients, Event History, Patterns and anti-patterns, the Resumable Activity,
versioning, serialization, encryption and claim-check. Its order and content
were ported as they stood, Segment for Segment, with the code rewritten as
idiomatic Java against SDK 1.40.0 and proven the same way: runnable
`snippets/`, tests that tie each slide's code to its snippet, runtime tests on
`TestWorkflowEnvironment`, and a rendered walkthrough check in CI.

On 2026-11-14 the Deck was brought up to `d9fbfcb` of the same source: the
opening retold as one bank transfer, slides reordered (Design patterns after
Activities, "Keep it in the Workflow" in Workflows, Resumable Activity after
Updates, the idempotency slides closing the Saga section), new Worker,
idempotency-key and compensation-Activity slides, and the Saga implementation
taught as pseudocode before the real Java. Later syncs follow the same way:
diff the source Deck from the last synced commit and port each change.

## Considered Options

`decks/temporal-essentials/` was rejected: the Python and TypeScript repos both
keep this Deck under `<language>-master/`, and Workshop Decks import Segments by
path, so a reader moving between the three repos finds the same layout under
the same kind of name. The Deck's title and README row still say Temporal
Essentials.

Porting from the Python Deck was rejected because it is a subset of the
TypeScript one.

## Consequences

The one-SDK rule means the Deck never says where it came from. Provenance lives
here and in the cost record, not in speaker notes.

Where Java 1.40.0 has no API for a feature a Segment covers, the slide stays,
explains the concept and says it is not yet in the Java SDK. It never shows an
invented API.

Segment file names follow the TypeScript Deck's, so a correction made in one
language's Segment can be found in the other's by name.
