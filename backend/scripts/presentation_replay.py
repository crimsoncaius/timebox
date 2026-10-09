"""Controlled fixture turns only; never a substitute for live candidate evaluation."""
import json

from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import AIMessageChunk, ToolMessage
from langchain_core.outputs import ChatGenerationChunk


class FixtureModel(BaseChatModel):
    steps: list[dict]

    @property
    def _llm_type(self):
        return "presentation-fixture"

    def bind_tools(self, tools, **kwargs):
        return self

    def _generate(self, messages, **kwargs):
        raise RuntimeError("FixtureModel supports native streaming only")

    async def _astream(self, messages, stop=None, run_manager=None, **kwargs):
        if not self.steps:
            raise RuntimeError("Fixture script exhausted")
        step = self.steps.pop(0)
        if "tool" in step:
            chunk = AIMessageChunk(content=step.get("preamble", ""), tool_call_chunks=[{
                "name": step["tool"], "args": json.dumps(step["args"]),
                "id": f"fixture-{len(self.steps)}", "index": 0}],
                response_metadata={"finish_reason": "tool_calls"})
        else:
            reads = []
            for message in messages:
                if isinstance(message, ToolMessage):
                    value = json.loads(message.content)
                    if isinstance(value, dict) and "snapshot_id" in value and value.get("kind") != "choices":
                        reads.append(value["snapshot_id"])
            selected = step.get("cards", [])
            ids = reads if selected == "all" else [reads[i] for i in selected]
            header = {"presentation": "snapshots", "snapshot_ids": ids} if ids else {"presentation": "none"}
            content = step.get("raw", json.dumps(header) + "\n" + step.get("answer", ""))
            chunk = AIMessageChunk(content=content, response_metadata={"finish_reason": step.get("finish", "stop")})
        generation = ChatGenerationChunk(message=chunk)
        if run_manager:
            await run_manager.on_llm_new_token(chunk.text, chunk=generation)
        yield generation
