package com.roklenarcic.util.strings;

import java.io.IOException;
import java.nio.CharBuffer;
import java.util.Iterator;

import com.roklenarcic.util.strings.threshold.RangeNodeThreshold;
import com.roklenarcic.util.strings.threshold.Thresholder;

// A set that matches only whole word matches. Non-word characters are user defined (with a default).
// Any non-word characters around input strings get trimmed. Non-word characters not allowed in the keywords
// and they will produce an IllegalArgumentException.
class WholeWordMatchMap<T> implements StringMap<T> {

    private boolean caseSensitive = true;
    private int charBufferSize = 0;
    private TrieNode<T> root;
    private Thresholder thresholdStrategy;
    private boolean[] wordChars;

    // Set where digits and letters, '-' and '_' are considered word characters.
    public WholeWordMatchMap(final Iterable<String> keywords, final Iterable<? extends T> values, boolean caseSensitive) {
        this(keywords, values, caseSensitive, new RangeNodeThreshold());
    }

    // Set where the characters in the given array are considered word characters
    public WholeWordMatchMap(final Iterable<String> keywords, final Iterable<? extends T> values, boolean caseSensitive, char[] wordCharacters) {
        this(keywords, values, caseSensitive, wordCharacters, new RangeNodeThreshold());
    }

    // Set where digits and letters and '-' and '_' are considered word characters but modified by the two
    // given arrays
    public WholeWordMatchMap(final Iterable<String> keywords, final Iterable<? extends T> values, boolean caseSensitive, char[] wordCharacters,
            boolean[] toggleFlags) {
        this(keywords, values, caseSensitive, wordCharacters, toggleFlags, new RangeNodeThreshold());
    }

    // Set where digits and letters and '-' and '_' are considered word characters but modified by the two
    // given arrays
    public WholeWordMatchMap(final Iterable<String> keywords, final Iterable<? extends T> values, boolean caseSensitive, char[] wordCharacters,
            boolean[] toggleFlags, Thresholder thresholdStrategy) {
        init(keywords, values, caseSensitive, WordCharacters.generateWordCharsFlags(wordCharacters, toggleFlags), thresholdStrategy);
    }

    // Set where the characters in the given array are considered word characters
    public WholeWordMatchMap(final Iterable<String> keywords, final Iterable<? extends T> values, boolean caseSensitive, char[] wordCharacters,
            Thresholder thresholdStrategy) {
        init(keywords, values, caseSensitive, WordCharacters.generateWordCharsFlags(wordCharacters), thresholdStrategy);
    }

    // Set where digits and letters, '-' and '_' are considered word characters.
    public WholeWordMatchMap(final Iterable<String> keywords, final Iterable<? extends T> values, boolean caseSensitive, Thresholder thresholdStrategy) {
        init(keywords, values, caseSensitive, WordCharacters.generateWordCharsFlags(), thresholdStrategy);
    }

    public void match(final Readable haystack, final ReadableMatchListener<T> listener) throws IOException {

        // Start with the root node.
        TrieNode<T> currentNode = root;
        // For each character.

        CharBuffer buf = CharBuffer.allocate(charBufferSize);

        // Putting this if into the loop worsens the performance so we'll sadly
        // have to deal with duplicated code.
        if (caseSensitive) {
            MAIN_LOOP: while (haystack.read(buf) != -1) {
                buf.flip();
                while (buf.hasRemaining()) {
                    final char c = buf.get();
                    TrieNode<T> nextNode = currentNode.getTransition(c);
                    // Regardless of the type of the character, we keep moving till we run into
                    // a situation where there's no transition available.
                    if (nextNode == null) {
                        if (!wordChars[c]) {
                            // If we ran into no-transition scenario on non-word character we can
                            // output the match on the current node if there is one.
                            // Later we will run through non-word characters to the start of the next word.
                            if (currentNode.matchLength != 0) {
                                if (!listener.match(currentNode.value)) {
                                    return;
                                }
                            }
                        } else {
                            // If we ran into no-transition situation on a word character, we scroll through
                            // word
                            // characters to a non-word character.
                            // Scroll to the first non-word character
                            if (scroll(haystack, buf, true, true)) {
                                currentNode = root;
                                break MAIN_LOOP;
                            }
                        }
                        // Scroll to the first word character
                        currentNode = root;
                        if (scroll(haystack, buf, false, true)) {
                            break MAIN_LOOP;
                        }
                    } else {
                        currentNode = nextNode;
                    }
                }
                buf.clear();
            }
            if (currentNode.matchLength != 0) {
                // Output any matches on the last node
                listener.match(currentNode.value);
            }
        } else {
            MAIN_LOOP2: while (haystack.read(buf) != -1) {
                buf.flip();
                while (buf.hasRemaining()) {
                    final char c = Character.toLowerCase(buf.get());
                    TrieNode<T> nextNode = currentNode.getTransition(c);
                    // Regardless of the type of the character, we keep moving till we run into
                    // a situation where there's no transition available.
                    if (nextNode == null) {
                        if (!wordChars[c]) {
                            // If we ran into no-transition scenario on non-word character we can
                            // output the match on the current node if there is one.
                            // Later we will run through non-word characters to the start of the next word.
                            if (currentNode.matchLength != 0) {
                                if (!listener.match(currentNode.value)) {
                                    return;
                                }
                            }
                        } else {
                            // If we ran into no-transition situation on a word character, we scroll through
                            // word
                            // characters to a non-word character.
                            // Scroll to the first non-word character
                            if (scroll(haystack, buf, true, false)) {
                                currentNode = root;
                                break MAIN_LOOP2;
                            }
                        }
                        // Scroll to the first word character
                        currentNode = root;
                        if (scroll(haystack, buf, false, false)) {
                            break MAIN_LOOP2;
                        }
                        currentNode = root;
                    } else {
                        currentNode = nextNode;
                    }
                }
                buf.clear();
            }
            if (currentNode.matchLength != 0) {
                // Output any matches on the last node
                listener.match(currentNode.value);
            }
        }
    }

    public void match(final String haystack, final MapMatchListener<T> listener) {

        // Start with the root node.
        TrieNode<T> currentNode = root;

        int idx = 0;
        // For each character.
        final int len = haystack.length();
        // Putting this if into the loop worsens the performance so we'll sadly
        // have to deal with duplicated code.
        if (caseSensitive) {
            while (idx < len) {
                char c = haystack.charAt(idx);
                TrieNode<T> nextNode = currentNode.getTransition(c);
                // Regardless of the type of the character, we keep moving till we run into
                // a situation where there's no transition available.
                if (nextNode == null) {
                    if (!wordChars[c]) {
                        // If we ran into no-transition scenario on non-word character we can
                        // output the match on the current node if there is one.
                        // Later we will run through non-word characters to the start of the next word.
                        if (currentNode.matchLength != 0) {
                            if (!listener.match(haystack, idx - currentNode.matchLength, idx, currentNode.value)) {
                                return;
                            }
                        }
                    } else {
                        // If we ran into no-transition situation on a word character, we scroll through word
                        // characters to a non-word character.
                        while (++idx < len && wordChars[haystack.charAt(idx)]) {
                            ;
                        }
                    }
                    // Scroll to the first word character
                    while (++idx < len && !wordChars[haystack.charAt(idx)]) {
                        ;
                    }
                    currentNode = root;
                } else {
                    ++idx;
                    currentNode = nextNode;
                }
            }
            if (currentNode.matchLength != 0) {
                // Output any matches on the last node
                listener.match(haystack, idx - currentNode.matchLength, idx, currentNode.value);
            }
        } else {
            while (idx < len) {
                char c = Character.toLowerCase(haystack.charAt(idx));
                TrieNode<T> nextNode = currentNode.getTransition(c);
                // Regardless of the type of the character, we keep moving till we run into
                // a situation where there's no transition available.
                if (nextNode == null) {
                    if (!wordChars[c]) {
                        // If we ran into no-transition scenario on non-word character we can
                        // output the match on the current node if there is one.
                        // Later we will run through non-word characters to the start of the next word.
                        if (currentNode.matchLength != 0) {
                            if (!listener.match(haystack, idx - currentNode.matchLength, idx, currentNode.value)) {
                                return;
                            }
                        }
                    } else {
                        // If we ran into no-transition situation on a word character, we scroll through word
                        // characters to a non-word character.
                        while (++idx < len && wordChars[haystack.charAt(idx)]) {
                            ;
                        }
                    }
                    // Scroll to the first word character
                    while (++idx < len && !wordChars[haystack.charAt(idx)]) {
                        ;
                    }
                    currentNode = root;
                } else {
                    ++idx;
                    currentNode = nextNode;
                }
            }
            if (currentNode.matchLength != 0) {
                // Output any matches on the last node
                listener.match(haystack, idx - currentNode.matchLength, idx, currentNode.value);
            }
        }
    }

    boolean[] getWordChars() {
        return wordChars;
    }

    private void init(final Iterable<String> keywords, final Iterable<? extends T> values, boolean caseSensitive, boolean[] wordChars,
            Thresholder thresholdStrategy) {
        Iterator<String> keywordsIter = keywords.iterator();
        Iterator<? extends T> valuesIter = values.iterator();
        this.thresholdStrategy = thresholdStrategy;
        this.wordChars = wordChars;
        int longestKeyword = 0;
        // Create the root node
        root = new HashmapNode<T>();
        // Add all keywords
        while (keywordsIter.hasNext() && valuesIter.hasNext()) {
            String keyword = keywordsIter.next();
            T value = valuesIter.next();
            // Skip any empty keywords
            if (keyword != null) {
                keyword = WordCharacters.trim(keyword, wordChars);
                // Don't allow words with non-word characters in them.
                for (int i = 0; i < keyword.length(); i++) {
                    if (!wordChars[keyword.charAt(i)]) {
                        throw new IllegalArgumentException(keyword + " contains non-word characters.");
                    }
                }
                if (keyword.length() > longestKeyword) {
                    longestKeyword = keyword.length();
                }
                if (keyword.length() > 0) {
                    // Start with the current node and traverse the tree
                    // character by character. Add nodes as needed to
                    // fill out the tree.
                    HashmapNode<T> currentNode = (HashmapNode<T>) root;
                    for (int idx = 0; idx < keyword.length(); idx++) {
                        currentNode = currentNode.getOrAddChild(caseSensitive ? keyword.charAt(idx) : Character.toLowerCase(keyword.charAt(idx)));
                    }
                    // Last node will contains the keyword as a match.
                    // Suffix matches will be added later.
                    currentNode.matchLength = keyword.length();
                    currentNode.value = value;
                }
            }
        }
        charBufferSize = longestKeyword > 2048 ? longestKeyword * 2 : 4096;
        // Go through nodes depth first, swap any hashmap nodes,
        // whose size is close to the size of range of keys with
        // flat array based nodes.
        root = optimizeNodes(root, 0);
    }

    // A recursive function that replaces hashmap nodes with range nodes
    // when appropriate.
    private final TrieNode<T> optimizeNodes(TrieNode<T> n, int level) {
        if (n instanceof HashmapNode) {
            HashmapNode<T> node = (HashmapNode<T>) n;
            char minKey = '\uffff';
            char maxKey = 0;
            // Find you the min and max key on the node.
            int size = node.numEntries;
            for (int i = 0; i < node.children.length; i++) {
                if (node.children[i] != null) {
                    node.children[i] = optimizeNodes(node.children[i], level + 1);
                    if (node.keys[i] > maxKey) {
                        maxKey = node.keys[i];
                    }
                    if (node.keys[i] < minKey) {
                        minKey = node.keys[i];
                    }
                }
            }
            // If difference between min and max key are small
            // or only slightly larger than number of entries, use a range node
            int keyIntervalSize = maxKey - minKey + 1;
            if (thresholdStrategy.isOverThreshold(size, level, keyIntervalSize)) {
                return new RangeNode<T>(node, minKey, maxKey);
            }
        }
        return n;
    }

    private boolean scroll(Readable haystack, CharBuffer buf, boolean wordChars, boolean caseSensitive) throws IOException {
        do {
            while (buf.hasRemaining()) {
                if (this.wordChars[caseSensitive ? buf.get() : Character.toLowerCase(buf.get())] != wordChars) {
                    buf.position(buf.position() - 1);
                    return false;
                }
            }
            buf.clear();
            if (haystack.read(buf) == -1) {
                return true;
            }
            buf.flip();
        } while (true);
    }

    // An open addressing hashmap implementation with linear probing
    // and capacity of 2^n
    private final static class HashmapNode<T> extends TrieNode<T> {

        // Start with capacity of 1 and resize as needed.
        @SuppressWarnings("unchecked")
        private TrieNode<T>[] children = new TrieNode[1];
        private char[] keys = new char[1];
        // Since capacity is a power of 2, we calculate mod by just
        // bitwise AND with the right mask.
        private int modulusMask = keys.length - 1;
        private int numEntries = 0;

        @SuppressWarnings("unchecked")
        @Override
        public void clear() {
            children = new TrieNode[1];
            keys = new char[1];
            modulusMask = keys.length - 1;
            numEntries = 0;
        }

        @Override
        public TrieNode<T> getTransition(final char key) {
            int defaultSlot = hash(key) & modulusMask;
            int currentSlot = defaultSlot;
            // Linear probing to find the entry for key.
            do {
                if (keys[currentSlot] == key) {
                    return children[currentSlot];
                } else if (children[currentSlot] == null) {
                    return null;
                } else {
                    currentSlot = ++currentSlot & modulusMask;
                }
            } while (currentSlot != defaultSlot);
            return null;
        }

        @Override
        public boolean isEmpty() {
            return numEntries == 0;
        }

        // Double the capacity of the node, calculate the new mask,
        // rehash and reinsert the entries
        @SuppressWarnings("unchecked")
        private void enlarge() {
            char[] biggerKeys = new char[keys.length * 2];
            TrieNode<T>[] biggerChildren = new TrieNode[children.length * 2];
            int biggerMask = biggerKeys.length - 1;
            for (int i = 0; i < children.length; i++) {
                char key = keys[i];
                TrieNode<T> node = children[i];
                if (node != null) {
                    int defaultSlot = hash(key) & biggerMask;
                    int currentSlot = defaultSlot;
                    do {
                        if (biggerChildren[currentSlot] == null) {
                            biggerKeys[currentSlot] = key;
                            biggerChildren[currentSlot] = node;
                            break;
                        } else if (biggerKeys[currentSlot] == key) {
                            throw new IllegalStateException();
                        } else {
                            currentSlot = ++currentSlot & biggerMask;
                        }
                    } while (currentSlot != defaultSlot);
                }
            }
            this.keys = biggerKeys;
            this.children = biggerChildren;
            this.modulusMask = biggerMask;
        }

        // Return the node for a key or create a new hashmap node for that key
        // and return that.
        private HashmapNode<T> getOrAddChild(char key) {
            // Check if we need to resize. Capacity of 2^16 doesn't need to resize.
            // If capacity is <16 and arrays are full or capacity is >16 and
            // arrays are 90% full, resize
            if (keys.length < 0x10000 && ((numEntries >= keys.length) || (numEntries > 16 && (numEntries >= keys.length * 0.90f)))) {
                enlarge();
            }
            int defaultSlot = hash(key) & modulusMask;
            int currentSlot = defaultSlot;
            do {
                if (children[currentSlot] == null) {
                    keys[currentSlot] = key;
                    HashmapNode<T> newChild = new HashmapNode<T>();
                    children[currentSlot] = newChild;
                    ++numEntries;
                    return newChild;
                } else if (keys[currentSlot] == key) {
                    return (HashmapNode<T>) children[currentSlot];
                } else {
                    currentSlot = ++currentSlot & modulusMask;
                }
            } while (currentSlot != defaultSlot);
            throw new IllegalStateException();
        }

        // FNV-1a hash
        private int hash(char c) {
            // HASH_BASIS = 0x811c9dc5;
            final int HASH_PRIME = 16777619;
            return (((0x811c9dc5 ^ (c >> 8)) * HASH_PRIME) ^ (c & 0xff)) * HASH_PRIME;
        }

    }

    // This node is good at representing dense ranges of keys.
    // It has a single array of nodes and a base key value.
    // Child at array index 3 has key of baseChar + 3.
    private static final class RangeNode<T> extends TrieNode<T> {

        private char baseChar = 0;
        private TrieNode<T>[] children;
        private int size = 0;

        @SuppressWarnings("unchecked")
        private RangeNode(HashmapNode<T> oldNode, char from, char to) {
            // Value of the first character
            this.baseChar = from;
            this.size = to - from + 1;
            this.value = oldNode.value;
            this.matchLength = oldNode.matchLength;
            // Avoid even allocating a children array if size is 0.
            if (size <= 0) {
                size = 0;
            } else {
                this.children = new TrieNode[size];
                // Grab the children of the old node.
                for (int i = 0; i < oldNode.children.length; i++) {
                    if (oldNode.children[i] != null) {
                        children[oldNode.keys[i] - from] = oldNode.children[i];
                    }
                }
            }
        }

        @Override
        public void clear() {
            children = null;
            size = 0;
        }

        @Override
        public TrieNode<T> getTransition(char c) {
            // First check if the key is between max and min value.
            // Here we use the fact that char type is unsigned to figure it out
            // with a single condition.
            int idx = (char) (c - baseChar);
            if (idx < size) {
                return children[idx];
            }
            return null;
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
        }

    }

    // Basic node for both
    private static abstract class TrieNode<T> {

        protected int matchLength;
        protected T value;

        public abstract void clear();

        // Get transition (root node returns something non-null for all characters - itself)
        public abstract TrieNode<T> getTransition(char c);

        public abstract boolean isEmpty();

    }

}