# The Assistant may make up to three reads per response

A response may now make up to three read-only tool calls, then at most one `propose_tracking`, before a final tool-free answer. This replaces the earlier guarantee of at most two model calls and one tool call. Comparison questions ("this week vs last week", "plan vs actual on Tuesday") and read-then-propose requests ("I've been doing what I planned since 2") need more than one tool. We chose a small bounded loop so the model can combine general-purpose Day and range reads. The alternative, one call with ever-wider tool parameters, would need a new parameter for every new question shape. A response may show up to three Assistant Cards, at most one per read.

## Consequences

Worst-case latency, token use and provider cost per response rise, and the 120-second deadline has to cover the extra calls. The bound is expected to change again, so anything that relies on per-response call counts should read the limit rather than assume a number.
