#!/bin/bash

START_COUNT=0
END_COUNT=10

while [[ $# -gt 0 ]]; do
  case $1 in
    --file)
      FILE="$2"
      shift
      shift
      ;;
    --help)
      echo "Options:"
      printf "--file Required. Specifies a file containing a list on hosts, one on each line to run addServer on\n"
      exit
      ;;
    --startCount)
      START_COUNT="$2"
      shift # past argument
      shift # past value
      ;;
    --endCount)
      END_COUNT="$2"
      shift # past argument
      shift # past value
      ;;
    -*|--*)
      echo "Unknown option $1"
      exit 1
      ;;
  esac
done

if [[ -n ${FILE+x} ]]; then
  readarray -t HOSTS < "$FILE"
  NUM_HOSTS="${#HOSTS[@]}"
else
  echo "--file is required"
  exit
fi

# Loop through the array
for INDEX in "${!HOSTS[@]}"
do
   ID=$((INDEX + 1))
   HOST="${HOSTS[$INDEX]}"
   echo "Creating server $ID on $HOST"
   ssh -f "ec2-user@${HOST}" "sh -c 'cd /home/ec2-user/Projects/jraft/experiments/; nohup ./addServer.sh --id ${ID} --size $NUM_HOSTS --seedIp ${HOSTS[0]} --startCount $START_COUNT --endCount $END_COUNT > stdout-log_$ID.log 2>&1 &'"
   sleep 1
done