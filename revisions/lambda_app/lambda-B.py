import os
import time
from pprint import pprint
from datetime import datetime


print(f"init static at {datetime.now()}")

def lambda_handler(event, context):
    flavor = "B"
    print(f"flavor: {flavor}")
    version = os.getenv("AWS_LAMBDA_FUNCTION_VERSION")
    print(f"version: {version}")
    print(f"start at {datetime.now()}")
    print("environment variables:")
    env = dict(os.environ)
    env.pop("AWS_SECRET_ACCESS_KEY", None)
    env.pop("AWS_SESSION_TOKEN", None)
    pprint(env)
    print("event:")
    pprint(event)
    print("context:")
    pprint(context)
    return {
        "version": version,
        "flavor": flavor
    }
