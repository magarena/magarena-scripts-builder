run: target/classes AllSets.json CardsMissingInMagarena.txt
	mvn exec:exec -Dexec.executable="java" -Dexec.args="-cp %classpath mtgjson.reader.MtgJsonReader"

clean:
	mvn clean
	-rm -rvf results

target/classes:
	mvn compile

%.txt: %.json
	jq -r '.cards | map(.name) | join("\n")' $^ | sort | uniq > $@

set ?= FRF

AllSets.json: ${set}.json
	jq '{"ABC": .}' $^ > $@

CardsMissingInMagarena.txt: ${set}.txt
	mv $^ $@
