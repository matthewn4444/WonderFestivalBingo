package com.matthewn4444.wonderfestbingo.utils;

import android.support.v4.util.Pair;

import java.text.ParseException;

public class SimpleHtmlParser {
    public int position;
    public final String html;

    public SimpleHtmlParser(String html) {
        this.position = 0;
        this.html = html;
    }

    public SimpleHtmlParser(String html, int pos) {
        this.position = pos;
        this.html = html;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int newPosition) {
        if (html.length() <= newPosition) {
            position = -1;
        } else {
            position = newPosition;
        }
    }

    public boolean isEndOfContent() {
        return position == -1;
    }

    /**
     * Pass in the HTML and the position of last search plus number of columns to skip. Uses
     * indexOf to find the <td> and skip them to move to the next column without parsing the
     * insides. Will throw if end of HTML. Throws exception when column does not exist.
     * @param numberOf columns skips
     * @throws ParseException
     */
    public void skipColumns(int numberOf) throws ParseException {
        for (int i = 0; i < numberOf; i++) {
            skipTag("td");
            if (position == -1) {
                throw new ParseException("Cannot skip column when no columns left.", 0);
            }
        }
    }

    /**
     * Passes the HTML, the tag you are looking for and the position, will skip that tag
     * and forward the position in the HTML. Will throw if end of HTML.
     * @param tag to skip
     * @throws ParseException
     */
    public void skipTag(String tag) throws ParseException {
        goToTag(tag);
        movePositionToEndOfTag(tag);
    }

    /**
     * Moves the position to the tag that is specified. Will throw if end of HTML
     * @param tag to get to
     * @throws ParseException
     */
    public void goToTag(String tag) throws ParseException {
        // Get the text inside the column
        String open = "<" + tag;
        position = html.indexOf(open, position);
        if (position == -1) {
            throw new ParseException("Cannot skip tag because open " + tag + " doesnt exist.", position);
        }
    }

    /**
     * Moves the position to the next tag that is specified.
     * @param tag to get to
     * @throws ParseException Will throw if end of HTML
     */
    public void goToNextTag(String tag) throws ParseException {
        // Get the text inside the column
        String open = "<" + tag;
        int pos = html.indexOf(open, position + 1);
        if (pos == -1) {
            throw new ParseException("Cannot skip tag because open " + tag + " doesnt exist.", position);
        }
        position = pos;
    }

    /**
     * Used to find the text inside the element (goes further down the children till reaches
     * text). This is not smart enough to pick up trailing text after embedded elements or
     * the same element in the element (such as a <div> inside another <div>).
     * @param tag to get text
     * @return text
     * @throws ParseException
     */
    public String getTextInNextElement(String tag) throws ParseException {
        return getTextInNextElement(html, tag, position);
    }

    /**
     * Recursively crawls an element's children and receives its text. Not smart enough for
     * major parsing but simple small tables.
     * @param text to search through
     * @param tag to find
     * @param pos start searching from
     * @return text in the child node
     * @throws ParseException
     */
    private String getTextInNextElement(String text, String tag, int pos) throws ParseException {
        Pair<Integer, String> result = htmlInTag(text, tag, pos);
        if (result == null) {
            throw new ParseException("Cannot find " + tag + " in html.", pos);
        }
        int holdPosition = result.first;
        text = result.second;

        if (!text.isEmpty() && text.charAt(0) == '<') {
            tag = findCurrentTag(text, 0);
            text = getTextInNextElement(text, tag, 0);
            position = holdPosition;
            return text;
        }

        text = text.replaceAll("&nbsp;", " ").trim();
        position = holdPosition;
        return text;
    }

    /**
     * Finds the text between end of current tag and next tag. Most of the time it will empty for
     * adjust tags. Text will only be found if this element is followed by a TextNode.
     * @return text between this element and this element
     * @throws ParseException
     */
    public String getTextAfterCurrentElement() throws ParseException {
        String tag = findCurrentTag(html, position);
        movePositionToEndOfTag(tag);
        int start = html.indexOf("<", position);
        if (start == -1) {
            throw new ParseException("Cannot find next tag", position);
        }
        return html.substring(position, start).trim();
    }

    /**
     * This finds the attribute's value inside the current element
     * @param attribute to find
     * @return the value inside the attribute or null if attribute doesnt exist
     * @throws ParseException
     */
    public String getAttributeInCurrentElement(String attribute) throws ParseException {
        int lessThan = html.lastIndexOf("<", position);
        if (lessThan == -1) {
            throw new ParseException("Cannot find attribute in current element", position);
        }
        position = lessThan;

        // See if we are in the closing tag, go to the last opening tag of the same tag
        if (html.charAt(position + 1) == '/') {
            String tag = findCurrentTag(html, position);
            position = lastIndexOfLowUpper(html, "<" + tag, position);
            if (position == -1) {
                throw new ParseException("Cannot find attribute in current element", position);
            }
        }

        // Find either the end of the tag or the attribute
        Pair<Integer, String> result = indexOfFirstOccurance(html, position, attribute + "=", ">");
        if (result == null) {
            return null;
        }
        if (result.second.equals(">")) {
            return null;
        }
        int attrStart = result.first + result.second.length();

        // See what type of quotes it is using and find the other quote that surrounds the value
        Character quoteChar = html.charAt(attrStart);
        if (quoteChar != '"' && quoteChar != '\'') {
            throw new ParseException("Cannot find attribute in current element. (Cannot parse attribute)", position);
        }
        attrStart++;
        int attrEnd = html.indexOf(quoteChar, attrStart);
        if (attrEnd == -1) {
            throw new ParseException("Cannot find attribute in current element. (Cannot parse attribute)", position);
        }
        return html.substring(attrStart, attrEnd);
    }

    /**
     * Finds the text inside the current html tag
     * Like getTextInNextElement, it will recusively look for the text
     * inside the tag. The current tag is where the current position inside
     * the html the parser is using.
     * @return text inside the current element
     * @throws ParseException
     */
    public String getTextInCurrentElement() throws ParseException {
        int lessThan = html.lastIndexOf("<", position);
        if (lessThan == -1) {
            throw new ParseException("Cannot find text in current element", position);
        }
        position = lessThan;
        String tag = findCurrentTag(html, position);
        return getTextInNextElement(tag);
    }

    /**
     * Gets the tag name of where the parser is current in. If it is not between a < and > then
     * it will throw
     * @return tag name if within the text of a tag
     * @throws throws if not currently in tag name
     */
    public String getCurrentTagName() throws ParseException {
        return findCurrentTag(html, position);
    }

    public boolean isCurrentInEmptyTag() throws ParseException {
        String tag = findCurrentTag(html, position);
        int beginning = html.lastIndexOf("<" + tag, position);
        if (beginning == -1) {
            throw new ParseException("Cannot check if current tag is empty", position);
        }
        Pair<Integer, String> result = indexOfFirstOccurance(html, beginning, "/>", ">");
        if (result == null) {
            throw new ParseException("Cannot check if current tag is empty", beginning);
        }
        return result.second.equals("/>");
    }

    /**
     * Gets the text from current position to specified text. Will throw if cannot find text.
     * After returned the new position will appear after the text you found
     * @param text to go to
     * @return string between current position and text specified
     * @throws ParseException
     */
    public String getStringToTextAndSkip(String text) throws ParseException {
        int oldPos = position;
        int pos = html.indexOf(text, position);
        if (pos == -1) {
            throw new ParseException("Cannot find text to get string for '" + text + "'", position);
        }
        position = pos + text.length();
        return html.substring(oldPos, pos);
    }

    /**
     * Creates a subset parser from current position to specified text for finding data within a
     * part of the text.
     * @param text parser to end at
     * @return another parser
     * @throws ParseException
     */
    public SimpleHtmlParser createNewParserFromHereToText(String text) throws ParseException {
        return new SimpleHtmlParser(getStringToTextAndSkip(text));
    }

    /**
     * Gets the tag string where the position is inside the text.
     * If you call this within text (not html tag), it will look for the parent tag
     * If you call this within a tag definition, it will find the name of that tag
     * This is not smart for complex html, you must use this with valid html
     * syntax.
     * @param text tag to search for
     * @param pos start from
     * @return tag
     * @throws ParseException
     */
    private String findCurrentTag(String text, int pos) throws ParseException {
        int lessThan = text.lastIndexOf("<", pos);
        if (lessThan == -1 || text.length() <= lessThan + 1) {
            throw new ParseException("Cannot find last tag in html.", pos);
        }
        // If captured the ending tag then skip the slash but find the tag name
        if (text.charAt(lessThan+1) == '/') {
            lessThan++;
        }

        Pair<Integer, String> result = indexOfFirstOccurance(text, lessThan, " ", ">");
        if (result == null) {
            throw new ParseException("Cannot find last tag in html.", pos);
        }
        return text.substring(lessThan + 1, result.first);
    }

    /**
     * Gets the text inside a TD. Very customized for web page tables.
     * Looks for the <td>, then <span> and if inside is an anchor tag <a>, then it will
     * find the text in that. Remove extra spaces and returns it.
     * Specify the HTML and its current position and it will return the position and text
     * it found. Will throw exceptions if end of HTML.
     * @return String
     */
    public String getTextInNextTD() throws ParseException {
        return getTextInNextElement("td");
    }

    /**
     * Moves the position after searched text and will return the next position.
     * If you have multiple text to skip, then add them as arguments, order does matter
     * as it will go from text to next text.
     * Throws error when text is not found.
     * @param textArr (can be multiple)
     * @return position
     * @throws ParseException
     */
    public int skipText(String... textArr) throws ParseException {
        int index, i;
        String text;
        for (i = 0; i < textArr.length; i++) {
            text = textArr[i];
            index = html.indexOf(text, position);
            if (index == -1) {
                throw new ParseException("Cannot find " + text + " in html.", position);
            }
            position = index + text.length();
        }
        return position;
    }

    /**
     * See if the text exists, if it does it will skip after it, if not it will return false
     * @param text to search for
     * @return whether it found the text or not
     */
    public boolean containsTextThenSkip(String text) {
        int index = this.html.indexOf(text, this.position);
        if (index == -1) return false;
        this.position = index + text.length();
        return true;
    }

    /**
     * Resets the position back to the beginning
     */
    public void reset() {
        position = 0;
    }

    /**
     * Finds the text of the tag you are looking for in the HTML. Will update the
     * position to end of the element. If cannot find, it will return null.
     * @param text to search through
     * @param tag to find
     * @param pos to look for
     * @return Pair (of positon and text), if not found will return null
     */
    private Pair<Integer, String> htmlInTag(String text, String tag, int pos) {
        // Get the text inside the column
        String open = "<" + tag, closing = "</" + tag + ">";
        int start = indexOfLowUpper(text, open, pos);
        if (start == -1) { return null; }
        start = text.indexOf(">", start);
        if (start == -1) { return null; }
        int end = indexOfLowUpper(text, closing, start);
        if (end == -1) { return null; }
        text = text.substring(++start, end);
        end += closing.length();
        return new Pair<>(end, text);
    }

    /**
     * Runs indexOf with upper and lower cases, finds first one
     * @param text to search from
     * @param search term
     * @param pos start position
     * @return min index
     */
    private int indexOfLowUpper(String text, String search, int pos) {
        int reg = text.indexOf(search, pos);
        int lower = text.indexOf(search.toLowerCase(), pos);
        int upper = text.indexOf(search.toUpperCase(), pos);
        if (lower == -1) {
            if (upper == -1) {
                return reg;
            } else if (reg == -1) {
                return upper;
            }
        } else if (upper == -1) {
            if (reg == -1) {
                return lower;
            }
            return Math.min(lower, reg);
        } else if (reg == -1) {
            return Math.min(lower, upper);
        }
        return Math.min(Math.min(lower, upper), reg);
    }

    /**
     * Runs lastIndexOf with upper and lower cases, finds first one
     * @param text to search from
     * @param search term
     * @param pos start position
     * @return min index
     */
    private int lastIndexOfLowUpper(String text, String search, int pos) {
        int lower = text.lastIndexOf(search.toLowerCase(), pos);
        int upper = text.lastIndexOf(search.toUpperCase(), pos);
        if (lower == -1) {
            return upper;
        } else if (upper == -1) {
            return lower;
        }
        return Math.min(lower, upper);
    }

    /**
     * This internal function will find the first occurance of one of the specfied strings
     * passed in.
     * For example, if you pass         indexOfFirstOccurance("foo", "bar", "thing");
     * it will look in the text for each and return the position and string that appears first
     *
     * For a sentence like    "I am Matthew and I like foo and bar with thing"
     * The first occurance would be "foo" at index 21
     * @param text This is the string to search
     * @param indexFrom Like indexOf, this index is where searching starts from
     * @param strings A list of words to search
     * @return a pair of the position and found text, null if cannot find any
     */
    private Pair<Integer, String> indexOfFirstOccurance(String text, int indexFrom, String... strings) {
        int[] positions = new int[strings.length];
        int smallest = text.length();
        int smallestIndex = -1;
        for (int i = 0; i < strings.length; i++) {
            positions[i] = text.indexOf(strings[i], indexFrom);

            // Record the index if this came first
            if (positions[i] != -1 && positions[i] < smallest) {
                smallestIndex = i;
                smallest = positions[i];
            }
        }

        // Could not find any of the strings in the text
        if (positions[smallestIndex] == -1) {
            return null;
        }
        return Pair.create(positions[smallestIndex], strings[smallestIndex]);
    }

    /**
     * Moves the position of the parser to the end of the closing tag of passed element.
     * @param tag the tag the parser moves to the end of
     * @throws ParseException
     */
    private void movePositionToEndOfTag(String tag) throws ParseException {
        String closing = "</" + tag + ">";
        position = html.indexOf(closing, position);
        if (position == -1) {
            throw new ParseException("Cannot skip tag because closing " + tag + " doesnt exist.", position);
        }
        position += closing.length();
    }
}