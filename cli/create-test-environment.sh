#!/usr/bin/env bash

pids=()

cd ../examples/image_variants/V1_purpleshirt/ && ids image upload 2 "person.png" &
pids[0]=$!

cd ../examples/image_variants/V2_stripedshirt/ && ids image upload 3 "person.png" &
pids[1]=$!

cd ../examples/image_variants/V3_purpleshirt_jacket/ && ids image upload 4 "person.png" &
pids[2]=$!

cd ../examples/image_variants/V4_purpleshirt_jacket_glasses/ && ids image upload 5 "person.png" &
pids[3]=$!

for pid in ${pids[*]}; do
  echo "Waiting for ${pid}"
  wait $pid
done