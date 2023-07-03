function get_stack_output() {
  key=$1
  jq -r 'map(select(.OutputKey == "'"${key}"'")) | .[0].OutputValue' <<< $stack_outputs
}