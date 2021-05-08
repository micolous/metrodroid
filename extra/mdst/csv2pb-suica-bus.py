#!/usr/bin/env python3
# -*- mode: python; indent-tabs-mode: nil; tab-width: 2; coding: utf-8 -*-
"""
sqlite2pb.py - converts sqlite3 stop database to MdST

Copyright 2018 Michael Farrell <micolous+git@gmail.com>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
"""
from __future__ import absolute_import, print_function
from argparse import ArgumentParser, FileType
import csv
import codecs
from stations_pb2 import Station
from mdst import MdstWriter

STATIONS_DB = '../../data/felica/iruca_stations.csv'
OUTPUT = 'suica_bus.mdst'

operators = {}
lines = {}

csvfile = codecs.getreader('utf-8-sig')(open(STATIONS_DB, "rb"))
csvreader = csv.DictReader(csvfile)

print('Building company and line list...')

counter = {}
for row in csvreader:
  counter[(row['CompanyName'],row['CompanyName_en'])] = counter.get((row['CompanyName'],row['CompanyName_en']), 0) + 1
  operators[row['CompanyName']] = (-1, row['CompanyName_en'])

i = 1
for k, v in sorted(counter.items(), key=lambda x: (-x[1],x[0][0])):
  operators[k[0]] = (i, operators[k[0]][1])
  i += 1

csvfile.seek(0)
csvreader.__next__()

# Note: a number of lines have the name Region and Line codes
# (eg: Tokyo Metro #3 Ginza, #8 Yūrakuchō, #9 Chiyoda)
# Need to actually extract this into a new sequence.
counter = {}
for row in csvreader:
  counter[(row['LineName'], row['LineName_en'])] = counter.get((row['LineName'], row['LineName_en']), 0) + 1
  lines[row['LineName']] = (-1, row['LineName_en'])

i = 1
for k, v in sorted(counter.items(), key=lambda x: (-x[1],x[0][0])):
  lines[k[0]] = (i, lines[k[0]][1])
  i += 1

csvfile.seek(0)
csvreader.__next__()

db = MdstWriter(
  fh=open(OUTPUT, 'wb'),
  version=1,
  local_languages=['ja'],
  tts_hint_language='ja',
  # Pivot the operator and line lists from k(ja)=v(id,en) to k(id)=v(en,ja)
  operators=dict(map(lambda v: (v[1][0], (v[1][1], v[0])), operators.items())),
  lines=dict(map(lambda v: (v[1][0], (v[1][1], v[0])), lines.items())),
)

print('Writing stations...')
# Now write out all the stations, and store their offset.
station_count = 0
for row in csvreader:
  operator_id = None
  if row['CompanyName'] is not None:
    operator_id = operators[row['CompanyName']][0]

  line_id = None
  if row['LineName'] is not None:
    line_id = lines[row['LineName']][0]
  
  # pack an int with the area/line/station code
  station_id = ((int(row['LineCode'], 16) & 0xff) << 8) + (int(row['StationCode'], 16) & 0xff)
  
  # create the record
  s = Station()
  s.id = station_id
  s.name.local = row['StationName']
  if row['StationName_en'] is not None:
    s.name.english = row['StationName_en']

  #else:
  #  print('Missing location: (%d) %s' % (s.id, s.name.english))
  if operator_id is not None:
    s.operator_id = operator_id
  if line_id is not None:
    s.line_id.append(line_id)
  
  db.push_station(s)
  station_count += 1

print('Building index...')
index_end_off = db.finalise()

print('Finished writing database.  Here\'s the stats:')
print(' - total ............ %8d stations' % station_count)
print('                      %8d bytes' % index_end_off)
print()
station_count = float(station_count)
print(' - header ........... %8d bytes' % db.stationlist_off)
stations_len = (db.index_off - db.stationlist_off)
print(' - stations ......... %8d bytes (%.1f per record)' % (stations_len, stations_len / station_count))
index_len = (index_end_off - db.index_off)
print(' - index ............ %8d bytes (%.1f per record)' % (index_len, index_len / station_count))


