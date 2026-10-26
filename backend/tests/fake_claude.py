"""A stand-in for the `claude` executable, for tests of ClaudeCodeCli.

Reads one stream-json message from stdin and answers like `claude -p` does.
The first argument is the mode (the real flags follow it):
  echo      structured_output.text = the text it received
  argv      structured_output.text = the arguments and env keys, as JSON
  wrong     reports another model in modelUsage
  slow      never answers
  garbage   prints non-JSON and exits
"""

import json
import os
import sys
import time

mode = sys.argv[1]
args = sys.argv[2:]
line = sys.stdin.readline()

if mode == "slow":
    time.sleep(60)
if mode == "garbage":
    print("not json")
    sys.exit(1)

message = json.loads(line)
text = message["message"]["content"][0]["text"]
if mode == "argv":
    text = json.dumps({"args": args, "env": sorted(os.environ), "cwd": os.getcwd()})
model = "claude-sonnet-5-5" if mode == "wrong" else "claude-haiku-5-5"

print(json.dumps({"type": "system", "subtype": "init", "model": model}))
print(
    json.dumps(
        {
            "type": "result",
            "subtype": "success",
            "is_error": False,
            "structured_output": {"text": text},
            "modelUsage": {model: {}},
            "usage": {"input_tokens": 1},
        }
    )
)
