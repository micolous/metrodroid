#!/usr/bin/env python3
# -*- mode: python; indent-tabs-mode: nil; tab-width: 2; coding: utf-8 -*-
"""
xml2pb.py - converts xml stop database to MdST

Copyright 2018 Google

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
import xml.etree.ElementTree as ET
from stations_pb2 import Station
from mdst import MdstWriter
import os
import stations_pb2

DB_PATH = '../../data/opus'
MAIN_FILE = 'operators.xml'
OUTPUT = 'opus.mdst'

operators = {}
lines = {}

tree = ET.parse(os.path.join(DB_PATH, MAIN_FILE))
root = tree.getroot()

print('Building company and line list...')

translations = {
  "METRO Ligne orange": "METRO Orange line",
  "METRO Ligne verte": "METRO Green line",
  "METRO Ligne jaune": "METRO Yellow line",
  "METRO Ligne bleue": "METRO Blue line"
}


def translate(s):
  if s in translations:
    return translations[s]
  return s

for operator in root.iter('operator'):
  operator_id = int(operator.attrib['id'])
  operator_pb = stations_pb2.Operator()
  operator_pb.name.local = operator.attrib['name']
  operator_pb.name.english = operator.attrib['name']
  operator_pb.default_transport = stations_pb2.BUS
  operators[operator_id] = operator_pb
  operator_xml_tree = ET.parse(os.path.join(DB_PATH, operator.attrib['file'] + '.xml'))
  operator_xml_root = operator_xml_tree.getroot()
  for line in operator_xml_root.iter('bus'):
    line_pb = stations_pb2.Line()
    line_id = line.attrib['id']
    line_name = line.attrib['name']
    line_pb.name.local = line_name
    line_pb.name.english = translate(line_name)
    if line_id == '':
      continue
    if line_name.startswith('METRO '):
      line_pb.transport = stations_pb2.METRO
    if line_name.startswith('TRAIN '):
      line_pb.transport = stations_pb2.TRAIN

    lines[(operator_id << 16)|int(line_id)] = line_pb

db = MdstWriter(
  fh=open(OUTPUT, 'wb'),
  version=1,
  local_languages=['fr'],
  tts_hint_language='fr',
  operators=operators,
  lines=lines,
)


print('Building index...')
db.finalise()

print('Finished writing database.')
