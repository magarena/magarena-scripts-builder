run: target/classes INPUT/AllPrintings.json INPUT/CardsMissingInMagarena.txt
	mvn exec:exec -Dexec.executable="java" -Dexec.args="-cp %classpath mtgjson.reader.MtgJsonReader"

clean:
	mvn clean
	-rm -rvf results

target/classes:
	mvn compile

%.txt: %.json
	jq -r '.cards | map(.name) | join("\n")' $^ | sort | uniq > $@

set ?= FRF

INPUT/AllPrintings.json:
	wget http://mtgjson.com/json/AllPrintings.json -O $@

#AllPrintings.json: ${set}.json
#	jq '{"ABC": .}' $^ > $@

#CardsMissingInMagarena.txt: ${set}.txt
#	mv $^ $@
