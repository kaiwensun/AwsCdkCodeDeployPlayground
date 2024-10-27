function get_stack_output() {
  key=$1
  jq -r 'map(select(.OutputKey == "'"${key}"'")) | .[0].OutputValue' <<< $stack_outputs
}

function get_region() {
  if [[ -n "$AWS_REGION" ]]; then
    echo "$AWS_REGION"
  elif [[ -n "$AWS_DEFAULT_REGION"]]; then
    echo "$AWS_DEFAULT_REGION"
  else
    aws configure get region
  fi
}