# The Assistant reads Supporting Notes and Task Descriptions only on explicit request

Supporting Notes and Task Descriptions used to be kept out of every Assistant tool result. Day reads now take a parameter that includes them, and the model sets it only when the user explicitly asks about notes, descriptions or what they wrote, including follow-ups to such a request. The rule is enforced by the prompt, not by a server check on the message, because a keyword check would refuse natural phrasings such as "what was I thinking during that block?". Other options were letting the model decide freely, which sends private text more often than needed, and adding a user Setting.

## Consequences

When included, this text goes to OpenRouter and is captured in full in Phoenix traces. It stays in the stored read snapshot and may remain in rolling response context. It is capped per item and treated as untrusted data, never instructions. Assistant Cards never show it.
