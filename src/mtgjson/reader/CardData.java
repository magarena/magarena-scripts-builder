package mtgjson.reader;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class CardData {

    public static final SortedMap<String, String> cardImageErrors = new TreeMap<>();

    private static void addCardImageError(final CardData card, final String errorDetails) {
        String key = card.cardName;
        if (!cardImageErrors.containsKey(key)) {
            cardImageErrors.put(key, errorDetails);
        }
    }

    private static void clearCardImageError(final CardData card) {
        String key = card.getCardName();
        if (cardImageErrors.containsKey(key)) {
            cardImageErrors.remove(key);
        }
    }

    public static String getId(final JsonObject card) {
        return card.get("name").getAsString().trim();
    }

    public static String getRarity(final JsonObject card) {
        return card.get("rarity").getAsString().substring(0, 1);
    }

    public static boolean isValid(final JsonObject jsonCard) {
        return !getRarity(jsonCard).contentEquals("S") &&
            (jsonCard.has("number") || jsonCard.has("multiverseid"));
    }

    private String cardName;
    private String imageUrl = "";
    private String rarity;
    private String manaCost;
    private String type;
    private String color = null;
    private String superType = null;
    private String subTypes = null;
    private String power = null;
    private String toughness = null;
    private String text = null;
    private String effectText = null;
    private String abilityText = null;
    private String oracleText = null;
    private final String setCode;

    public CardData(final JsonObject jsonCard, final String setCode) {

        this.setCode = setCode;

        extractCardName(jsonCard);
        extractImageUrl(jsonCard);
        extractRarity(jsonCard);
        extractManaCost(jsonCard);
        extractColor(jsonCard);
        extractPower(jsonCard);
        extractToughness(jsonCard);
        extractSuperTypes(jsonCard);
        extractTypes(jsonCard);
        extractSubTypes(jsonCard);

        if (jsonCard.has("text")) {
            setText(jsonCard.get("text").getAsString());
            if (jsonCard.has("types") && (getType().contains("Instant") || getType().contains("Sorcery"))) {
                extractEffectText(jsonCard);
                extractAbilitiesFromEffects();
            } else {
                extractAbilityText(jsonCard);
            }
            extractOracleText(jsonCard);
        }
    }

    private void extractAbilitiesFromEffects() {
        performPatternReplace(Pattern.compile("^Devoid~"));
        performPatternReplace(Pattern.compile("As an additional cost to cast SN, [^.]*\\.~"));
        performPatternReplace(Pattern.compile("Convoke~"));
        performPatternReplace(Pattern.compile("Delve~"));
        performPatternReplace(Pattern.compile("Buyback[^~]*~"));
        performPatternReplace(Pattern.compile("Kicker[^~]*~"));
        performPatternReplace(Pattern.compile("Surge[^~]*~"));
        performPatternReplace(Pattern.compile("Cast SN only [^.]*\\.~"));
        performPatternReplace(Pattern.compile("~Flashback.*"));
        performPatternReplace(Pattern.compile("~Conspire"));
    }

    private void performPatternReplace(Pattern pattern){
        Matcher matcher = pattern.matcher(effectText);
        if (matcher.find()) {
            abilityText = abilityText == null ? matcher.group(0).replace("~", "") : abilityText + ";\\\n        " + matcher.group(0).replace("~", "");
            effectText = pattern.matcher(effectText).replaceFirst("");
        }
    }

    private void extractAbilityText(final JsonObject json) {
        abilityText = json.get("text").getAsString()
                .replaceAll("^\\(.+?\\)\n", "")
                .replaceAll("^.+— ", "")
                .replace("\n", ";\\\n        ")
                .replace(";\\\n        •", " \\\n        •")
                .replaceAll(" \\(.+?\\)", "")
                .replaceFirst(" •", " (1)")
                .replaceFirst(" •", " (2)")
                .replaceFirst(" •", " (3)")
                .replaceFirst(" •", " (4)")
                .replaceAll("\n.+ — ", "\n        ")
                .replace(getCardName(), "SN")
                .replaceAll("named SN","named "+getCardName());
    }

    private void extractCardName(final JsonObject json) {
        cardName = json.get("name").getAsString();
    }

    private void extractColor(final JsonObject json) {
        if (json.has("colors") && json.has("manaCost") == false) {
            color = json.get("colors")
                .toString()
                .replace("Blue", "U")
                .replaceAll("\\W", "")
                .replaceAll("[a-z]", "")
                .toLowerCase();
        }
    }

    private void extractEffectText(final JsonObject json) {
        effectText = json.get("text").getAsString()
            .replaceAll("^\\(.+?\\)\n", "")
            .replaceAll("^.+— ", "")
            .replace("\n", "~")
            .replaceAll("~.+? — ", "~")
            .replaceAll(" \\(.+?\\)", "")
            .replaceAll("\n~•", "~•")
            .replaceFirst("~•", " (1)")
            .replaceFirst("~•", " (2)")
            .replaceFirst("~•", " (3)")
            .replaceFirst("~•", " (4)")
            .replaceAll("\n.+ — ", "\n        ")
            .replace(getCardName(), "SN")
            .replaceAll("named SN", "named " + getCardName());
    }

    private void extractImageUrl(final JsonObject json) {

        if (json.has("number")) {
            imageUrl = String.format(
                "http://magiccards.info/scans/en/%s/%s.jpg",
                setCode.toLowerCase(),
                json.get("number").getAsString()
            );
            clearCardImageError(this);

        } else if (json.has("multiverseid")) {
            imageUrl = setCode.toLowerCase();
            clearCardImageError(this);

        } else {
            addCardImageError(this, String.format("%s has no number or multiverseid - cannot set {image} property.", cardName));
        }

    }

    private void extractManaCost(final JsonObject json) {
        if (json.has("manaCost")) {
            manaCost = json.get("manaCost").getAsString();
        }
    }

    private void extractOracleText(final JsonObject json) {
        if (json.has("text")) {
            oracleText = json.get("text").getAsString()
                .replaceAll("^\\(.+?\\)\n", "")
                .replaceAll(" \\(.+?\\)", "")
                .replaceAll("\n", "\\\\n")
                .replaceAll(";", ",");
        }
    }

    private void extractPower(final JsonObject json) {
        if (json.has("power")) {
            power = json.get("power").getAsString();
        }
    }

    private void extractRarity(final JsonObject json) {
        rarity = CardData.getRarity(json);
    }

    private void extractSubTypes(final JsonObject json) {
        if (json.has("subtypes")) {
            StringBuffer sb = new StringBuffer();
            JsonArray cardTypes = json.getAsJsonArray("subtypes");
            for (int j = 0; j < cardTypes.size(); j++) {
                String subType = cardTypes.get(j).toString();
                sb.append(subType
                    .replace("\"", "")
                    .replace(" ", "_")
                    .replace("’s", "'s")
                    .replace("-", "_")).append(",");
            }
            subTypes = sb.toString().substring(0, sb.toString().length() - 1);
        }
    }

    private void extractSuperTypes(final JsonObject json) {
        if (json.has("supertypes")) {
            StringBuilder sb = new StringBuilder();
            JsonArray cardTypes = json.getAsJsonArray("supertypes");
            for (int j = 0; j < cardTypes.size(); j++) {
                sb.append(cardTypes.get(j).toString().replace("\"", "")).append(",");
            }
            superType = sb.toString().substring(0, sb.toString().length() - 1);
        }
    }

    private void extractToughness(JsonObject json) {
        if (json.has("toughness")) {
            toughness = json.get("toughness").getAsString();
        }
    }

    private void extractTypes(JsonObject json) {
        if (json.has("types")) {
            StringBuilder sb = new StringBuilder();
            JsonArray cardTypes = json.getAsJsonArray("types");
            for (int j = 0; j < cardTypes.size(); j++) {
                sb.append(cardTypes.get(j).toString().replace("\"", "")).append(",");
            }
            type = sb.toString().substring(0, sb.toString().length() - 1);
        }
    }

    public String getAbilityText() {
        return abilityText;
    }

    public String getCardName() {
        return cardName;
    }

    private String getCardNameAsAscii() {
        return cardName
            .replace("Æ", "_")
            .replace("á", "_")
            .replace("à", "_")
            .replace("â", "_")
            .replace("é", "_")
            .replace("í", "_")
            .replace("ö", "_")
            .replace("ú", "_")
            .replace("û", "_");
    }

    public String getColor() {
        return color;
    }

    public String getEffectText() {
        return effectText;
    }

    public String getFilename() {
        return getCardNameAsAscii().replaceAll("[^A-Za-z0-9]", "_") + ".txt";
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getManaCost() {
        return manaCost;
    }

    public String getOracleText() {
        return oracleText;
    }

    public String getPower() {
        return power;
    }

    public String getRarity() {
        return rarity;
    }

    public String getSubType() {
        return subTypes;
    }

    public String getText() {
        return text;
    }

    public String getTiming() {
        if (hasAbilityText()) {
            if (abilityText.contains("Flash") && !abilityText.contains("Flashback")) {
                return "flash";
            }
        }
        if (type.contains("Instant")) {
            if (hasEffectText()) {
                if (effectText.contains("Counter")) {
                    return "counter";
                }
            }
            return "removal";
        }
        if (type.contains("Land")) {
            return "land";
        }
        if (hasSubType()) {
            if (subTypes.contains("Equipment")) {
                return "equipment";
            }
            if (subTypes.contains("Aura")) {
                return "aura";
            }
        }
        if (type.contains("Creature")) {
            if (hasAbilityText()) {
                if (abilityText.contains("Haste")) {
                    return "fmain";
                }
                if (abilityText.contains("Defender")) {
                    return "smain";
                }
            }
            return "main";
        }
        if (type.contains("Artifact")) {
            return "artifact";
        }
        if (type.contains("Enchantment")) {
            return "enchantment";
        }
        return "main";
    }

    public String getToughness() {
        return toughness;
    }

    public String getType() {
        return superType != null ? String.format("%s,%s", superType, type) : type;
    }

    public boolean hasAbilityText() {
        return getText() != null && getAbilityText() != null;
    }

    public boolean hasColor() {
        return !hasManaCost() && getColor() != null;
    }

    public boolean hasEffectText() {
        return getText() != null && getEffectText() != null;
    }

    public boolean hasManaCost() {
        return getManaCost() != null;
    }

    public boolean hasOracleText() {
        return getText() != null;
    }

    public boolean hasPT() {
        return getPower() != null && getToughness() != null;
    }

    public boolean hasSubType() {
        return getSubType() != null;
    }

    public void setText(String cardText) {
        text = cardText;
    }

}
