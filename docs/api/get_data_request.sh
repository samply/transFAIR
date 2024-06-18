#! /usr/bin/env bash
# Call with uuid as first parameter, e.g. ./get_data_requests.sh 30d39dec-7617-4bcb-a001-73561507d06f
curl http://localhost:8080/requests/$1