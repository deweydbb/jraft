#!/bin/bash
echo "doing cleanup"

for dir in server*/
do
  rm -rf "$dir"
done

rm *.log

rm -rf "client/"