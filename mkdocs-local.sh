#!/usr/bin/env sh

docker run --rm -it -p 8000:8000 -v ${PWD}/mkdocs:/docs squidfunk/mkdocs-material:8.5.10
