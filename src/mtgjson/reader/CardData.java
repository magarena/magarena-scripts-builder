package mtgjson.reader;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private String color;
    private String superType;
    private String subTypes;
    private String power;
    private String toughness;
    private String text;
    private String effectText;
    private String abilityText;
    private String oracleText;
    private String loyalty;
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

        if (jsonCard.has("loyalty")) {
            extractLoyalty(jsonCard);
        }

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
        effectToAbility(Pattern.compile("^Devoid~"));
        effectToAbility(Pattern.compile("^Changeling~"));
        effectToAbility(Pattern.compile("^Split second~"));
        effectToAbility(Pattern.compile("As an additional cost to cast SN, [^.]*\\.~"));
        effectToAbility(Pattern.compile("SN costs [^.]*\\.~"));
        effectToAbility(Pattern.compile("Convoke~"));
        effectToAbility(Pattern.compile("Delve~"));
        effectToAbility(Pattern.compile("Buyback[^~]*~"));
        effectToAbility(Pattern.compile("Kicker[^~]*~"));
        effectToAbility(Pattern.compile("Surge[^~]*~"));
        effectToAbility(Pattern.compile("Replicate[^~]*~"));
        effectToAbility(Pattern.compile("Multikicker[^~]*~"));
        effectToAbility(Pattern.compile("~Cycling.*"));
        effectToAbility(Pattern.compile("~Basic landcycling.*"));
        effectToAbility(Pattern.compile("~Flashback.*"));
        effectToAbility(Pattern.compile("~Entwine.*"));
        effectToAbility(Pattern.compile("~Conspire"));
        effectToAbility(Pattern.compile("Madness[^~]*~"));
        effectToAbility(Pattern.compile("~Madness[^~]*"));
        effectToAbility(Pattern.compile("Cast SN only [^.]*\\.~"));
        effectToAbility(Pattern.compile("~Storm"));
        effectToAbility(Pattern.compile("^Undaunted~"));
        effectToAbility(Pattern.compile("~Miracle.*"));
        effectToAbility(Pattern.compile("Haunt[^.]*\\.~"));
        effectToAbility(Pattern.compile("~Haunt"));
        effectToAbility(Pattern.compile("Cascade~"));
        effectToAbility(Pattern.compile("Affinity for [^.]*\\.~"));
        effectToAbility(Pattern.compile("~Awaken.*"));
        effectToAbility(Pattern.compile("~Transmute.*"));
        effectToAbility(Pattern.compile("~Retrace"));
        effectToAbility(Pattern.compile("~Fuse"));
        effectToAbility(Pattern.compile("~Overload.*"));
        effectToAbility(Pattern.compile("~Reinforce.*"));
        effectToAbility(Pattern.compile("^Affinity for [^~]*~"));
        effectToAbility(Pattern.compile("Prowl[^~]*~"));
        effectToAbility(Pattern.compile("~Splice onto Arcane.*"));
        effectToAbility(Pattern.compile("^You may [^.]* rather than pay SN's mana cost\\.~"));
    }

    private void effectToAbility(Pattern pattern){
        Matcher matcher = pattern.matcher(effectText);
        if (matcher.find()) {
            abilityText = (abilityText == null) ? matcher.group(0).replaceFirst("(^~|~(?!.))", "").replaceAll("~", ";\\\\\n        ") : abilityText + ";\\\n        " + matcher.group(0).replaceFirst("^~", "").replaceAll("~", ";\\\\\n        ");
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
                .replace(cardName, "SN")
                .replaceAll("named SN","named "+ cardName);
    }

    private void extractCardName(final JsonObject json) {
        cardName = json.get("name").getAsString();
    }

    private void extractColor(final JsonObject json) {
        if (json.has("colors") && !json.has("manaCost")) {
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
            .replace(cardName, "SN")
            .replaceAll("named SN", "named " + cardName);
    }

    private void extractImageUrl(final JsonObject json) {

        if (json.has("number")) {
            imageUrl = String.format(
                "http://magiccards.info/scans/en/%s/%s.jpg",
                setCode.toLowerCase(Locale.ENGLISH),
                json.get("number").getAsString()
            );
            clearCardImageError(this);

        } else if (json.has("multiverseid")) {
            imageUrl = setCode.toLowerCase(Locale.ENGLISH);
            clearCardImageError(this);

        } else {
            addCardImageError(this, String.format("%s has no number or multiverseid - cannot set {image} property.", cardName));
        }

    }

    private void extractLoyalty(final JsonObject json) {
        if (json.has("loyalty")) {
            loyalty = json.get("loyalty").getAsString();
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
        rarity = getRarity(json);
    }

    private void extractSubTypes(final JsonObject json) {
        if (json.has("subtypes")) {
            StringBuilder sb = new StringBuilder();
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

    public String getLoyalty() {
        return loyalty;
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

    public String getEnchant() {
        return "default,creature";
    }

    public String getTiming() {
        if (hasAbilityText()) {
            if (abilityText.contains("Flash") && !abilityText.contains("Flashback")) {
                return "flash";
            }
            if (abilityText.contains("Storm")) {
                return "storm";
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
                if (abilityText.contains("Haste") || abilityText.contains("Bolster") || abilityText.contains("Exalted")) {
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
        return text != null && abilityText != null;
    }

    public boolean hasColor() {
        return !hasManaCost() && color != null;
    }

    public boolean hasEffectText() {
        return text != null && effectText != null;
    }

    public boolean hasLoyalty() {
        return loyalty != null;
    }

    public boolean hasManaCost() {
        return manaCost != null;
    }

    public boolean hasOracleText() {
        return text != null;
    }

    public boolean hasPT() {
        return power != null && toughness != null;
    }

    public boolean hasSubType() {
        return subTypes != null;
    }

    public void setText(String cardText) {
        text = cardText;
    }

}
