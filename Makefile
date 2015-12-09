run: target/classes INPUT/AllSets.json INPUT/CardsMissingInMagarena.txt
	mvn exec:exec -Dexec.executable="java" -Dexec.args="-cp %classpath mtgjson.reader.MtgJsonReader"

clean:
	mvn clean
	-rm -rvf results

target/classes:
	mvn compile

%.txt: %.json
	jq -r '.cards | map(.name) | join("\n")' $^ | sort | uniq > $@

set ?= FRF

INPUT/AllSets.json:
	wget http://mtgjson.com/json/AllSets.json -O $@

#AllSets.json: ${set}.json
#	jq '{"ABC": .}' $^ > $@

#CardsMissingInMagarena.txt: ${set}.txt
#	mv $^ $@
