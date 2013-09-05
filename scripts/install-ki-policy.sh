#!/bin/bash


DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

ln -s $DIR/pre-commit.no-tabs $DIR/../.git/hooks/pre-commit.no-tabs
