/**
 *
 */
package mtgjson.reader;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class CardData {

    private String cardName;
    private String imageUrl;
    private String rarity;
    private String manaCost;
    private String type;
    private String color        = null;
    private String superType    = null;
    private String subType      = null;
    private String power        = null;
    private String toughness    = null;
    private String text         = null;
    private String effectText   = null;
    private String abilityText  = null;
    private String oracleText   = null;

    public CardData(final JsonObject card) throws UnsupportedEncodingException {

        setCardName(card.get("name").getAsString());
        setImageUrl("http://mtgimage.com/card/" + URLEncoder.encode(card.get("imageName").getAsString(), "UTF-8").replace("+", "%20").replaceAll("(\\D)1", "$1") + ".jpg");
        setRarity(card.get("rarity").getAsString().substring(0, 1));

        if (card.has("manaCost"))   { setManaCost(card.get("manaCost").getAsString()); }
        
        if (card.has("colors") && card.has("manaCost")==false) { 
            final String colors = card.get("colors")
                    .toString()
                    .replaceAll("\\W", "")
                    .replaceAll("[a-z]", "")
                    .toLowerCase();
            setColor(colors);
        }
        
        if (card.has("power"))      { setPower(card.get("power").getAsString()); }
        if (card.has("toughness"))  { setToughness(card.get("toughness").getAsString()); }
        if (card.has("text"))       { setText(card.get("text").getAsString()); }

        if (card.has("supertypes")) {
            final StringBuffer sb = new StringBuffer();
            final JsonArray cardTypes = card.getAsJsonArray("supertypes");
            for (int j = 0; j < cardTypes.size(); j++) {
                sb.append(cardTypes.get(j).toString().replace("\"", "")).append(",");
            }
            final String superTypes =sb.toString().substring(0,sb.toString().length()-1);
            setSuperType(superTypes);
        }
        
        if (card.has("types")) {
            final StringBuffer sb = new StringBuffer();
            final JsonArray cardTypes = card.getAsJsonArray("types");
            for (int j = 0; j < cardTypes.size(); j++) {
                sb.append(cardTypes.get(j).toString().replace("\"", "")).append(",");
            }
            final String typeValues = sb.toString().substring(0, sb.toString().length() - 1);
            setType(typeValues);
        }

        if (card.has("subtypes")) {
            final StringBuffer sb = new StringBuffer();
            final JsonArray cardTypes = card.getAsJsonArray("subtypes");
            for (int j = 0; j < cardTypes.size(); j++) {
                final String subType = cardTypes.get(j).toString();
                sb.append(subType
                            .replace("\"", "")
                            .replace(" ", "_")
                            .replace("’s", "'s")
                            .replace("-", "_")).append(",");
            }
            final String typeValues = sb.toString().substring(0, sb.toString().length() - 1);
            setSubType(typeValues);
        }
        
        if (card.has("text")) {
            if (card.has("types") && (type.contains("Instant") || type.contains("Sorcery"))) {
                final String effect = text
                                        .replaceAll("^\\(.+?\\)\n","")
                                        .replaceAll("^.+— ", "")
                                        .replace("\n", "~")
                                        .replaceAll("~.+? — ","~")
                                        .replaceAll(" \\(.+?\\)", "")
                                        .replaceAll("~•", " •")
                                        .replace(getCardName(false), "SN");
                setEffectText(effect);
            } else {
                final String ability = text
                                        .replaceAll("^\\(.+?\\)\n","")
                                        .replaceAll("^.+— ", "")
                                        .replace("\n", ";\\\n        ")
                                        .replaceAll(" \\(.+?\\)", "")
                                        .replace(getCardName(false), "SN");
                setAbilityText(ability);
            }
            final String oracle = text
                                    .replaceAll("^\\(.+?\\)\n", "")
                                    .replace(".\n", ". ")
                                    .replace("\n", ". ")
                                    .replaceAll(" \\(.+?\\)", "")
                                    .replace("..", ".")
                                    .replace("—.", "—")
                                    .replace(". .", ".");
            setOracleText(oracle);
        }
    }

    public String getCardName(final boolean replaceNonAscii) {
        if (replaceNonAscii) {
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
        } else {
            return cardName;
        }
    }
    public void setCardName(String cardName) {
        this.cardName = cardName;
    }

    public String getImageUrl() {
        return imageUrl;
    }
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getRarity() {
        return rarity;
    }
    public void setRarity(String rarity) {
        this.rarity = rarity;
    }

    public String getManaCost() {
        return manaCost;
    }
    public void setManaCost(String manaCost) {
        this.manaCost = manaCost;
    }
    
    public String getColor() {
        return color;
    }
    
    public void setColor(String color) {
        this.color = color;
    }

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }

    public String getFilename() {
        String filename = getCardName(true);
        return filename
                .replace(" ", "_")
                .replace(",", "_")
                .replace("'", "_")
                .replace("-", "_")
                .replace(":", "_")
                .replace('"', '_')
                + ".txt";
    }

    public String getPower() {
        return power;
    }
    public void setPower(String power) {
        this.power = power;
    }

    public String getToughness() {
        return toughness;
    }
    public void setToughness(String toughness) {
        this.toughness = toughness;
    }

    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
    }

    public String getSuperType() {
        return superType;
    }
    
    public void setSuperType(String superType) {
        this.superType = superType;
    }
    
    public String getSubType() {
        return subType;
    }
    public void setSubType(String subType) {
        this.subType = subType;
    }
    
    public String getEffectText() {
        return effectText;
    }
    public void setEffectText(String effect) {
        this.effectText = effect;
    }

    public String getAbilityText() {
        return abilityText;
    }
    public void setAbilityText(String ability) {
        this.abilityText = ability;
    }

    public String getOracleText() {
        return oracleText;
    }

    public void setOracleText(String oracleText) {
        this.oracleText = oracleText;
    }
}
