#!/bin/bash

set -e

BASE="2.303.2-lts"

function usage() {
  printf "Usage: $0 [-b ]:  Jenkins base image. You can sepcify the Jenkins base image version.\n \
                            Defaults to $BASE.\n"
}                           

while getopts ":b:" options; do
  case "${options}" in
    b)
      BASE=${OPTARG}
      echo "Building using $BASE as jenkins base image."
      ;;
    *)
      usage
      ;;
    esac
done

docker-compose build --build-arg JENKINS_VER="${BASE}"