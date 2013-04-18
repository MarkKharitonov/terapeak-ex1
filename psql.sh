#!/bin/sh -f
sudo -u postgres /usr/bin/psql -d exdb -p 5432 $*
