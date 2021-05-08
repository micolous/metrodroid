# -*- mode: makefile; indent-tabs-mode: tabs; tab-width: 2 -*-

.PHONY: all clean

all: stations_pb2.py mdsts

stations_pb2.py: stations.proto
	protoc --python_out=. stations.proto

clean:
	rm -f stations_pb2.py
	rm -f $(MDSTS)
	rm -f $(MDSTS:.mdst=.csv)

MDSTS += suica_rail.mdst
suica_rail.mdst: csv2pb-suica.py stations_pb2.py mdst.py ../../data/felica/stations.csv
	python3 csv2pb-suica.py

MDSTS += suica_bus.mdst
suica_bus.mdst: csv2pb-suica-bus.py stations_pb2.py ../../data/felica/iruca_stations.csv
	python3 csv2pb-suica-bus.py

MDSTS += ovc.mdst
ovc.mdst: tsv2pb-ovc.py stations_pb2.py mdst.py ../../third_party/ovc-tools/stations/*.tsv
	python3 tsv2pb-ovc.py

MDSTS += podorozhnik.mdst
podorozhnik.mdst: csv2pb-podorozhnik.py stations_pb2.py mdst.py ../../data/podorozhnik/*.csv
	python3 csv2pb-podorozhnik.py

MDSTS += opus.mdst
opus.mdst: xml2pb-opus.py stations_pb2.py mdst.py ../../data/opus/*.xml
	python3 xml2pb-opus.py

%.csv: %.mdst dump2csv.py stations_pb2.py
	python3 dump2csv.py -o $@ $<

mdsts: $(MDSTS) $(MDSTS:.mdst=.csv)

