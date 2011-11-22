/*
 * Copyright (C) 2011 Miami-Dade County.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Note: this file incorporates source code from 3d party entities. Such code
 * is copyrighted by those entities as indicated below.
 */

/*
 * This code is based on the mjson library found here:
 *     http://www.sharegov.org/mjson/Json.java
 *     http://sharegov.blogspot.com/2011/06/json-library.html
 */

package org.astrogrid.samp;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple JSON parser which only copes with SAMP-friendly JSON,
 * that is strings, lists and objects.
 * This code is a stripped-down and somewhat fixed copy of the mjson
 * libraray written by Borislav Iordanov, from
 * <a href="http://www.sharegov.org/mjson/Json.java"
 *         >http://www.sharegov.org/mjson/Json.java</a>.
 *
 * @author Borislav Iordanov
 * @author Mark Taylor
 */
class JsonReader {
    private static final Object OBJECT_END = new Token("OBJECT_END");
    private static final Object ARRAY_END = new Token("ARRAY_END");
    private static final Object COLON = new Token("COLON");
    private static final Object COMMA = new Token("COMMA");
    public static final int FIRST = 0;
    public static final int CURRENT = 1;
    public static final int NEXT = 2;

    private static Map escapes = new HashMap();
    static 
    {
        escapes.put(new Character('"'), new Character('"'));
        escapes.put(new Character('\\'), new Character('\\'));
        escapes.put(new Character('/'), new Character('/'));
        escapes.put(new Character('b'), new Character('\b'));
        escapes.put(new Character('f'), new Character('\f'));
        escapes.put(new Character('n'), new Character('\n'));
        escapes.put(new Character('r'), new Character('\r'));
        escapes.put(new Character('t'), new Character('\t'));
    }

    private CharacterIterator it;
    private char c;
    private Object token;
    private StringBuffer buf = new StringBuffer();

    private char next() 
    {
        if (it.getIndex() == it.getEndIndex())
            throw new DataException("Reached end of input at the " + 
                                    it.getIndex() + "th character.");
        c = it.next();
        return c;
    }

    private char previous()
    {
        c = it.previous();
        return c;
    }
    
    private void skipWhiteSpace() 
    {
        do
        {
            if (Character.isWhitespace(c))
                ;
            else if (c == '/')
            {
                next();
                if (c == '*')
                {
                    // skip multiline comments
                    while (c != CharacterIterator.DONE)
                        if (next() == '*' && next() == '/')
                                break;
                    if (c == CharacterIterator.DONE)
                        throw new DataException("Unterminated comment while parsing JSON string.");
                }
                else if (c == '/')
                    while (c != '\n' && c != CharacterIterator.DONE)
                        next();
                else
                {
                    previous();
                    break;
                }
            }
            else
                break;
        } while (next() != CharacterIterator.DONE);
    }

    public Object read(CharacterIterator ci, int start) 
    {
        it = ci;
        switch (start) 
        {
            case FIRST:
                c = it.first();
                break;
            case CURRENT:
                c = it.current();
                break;
            case NEXT:
                c = it.next();
                break;
        }
        return read();
    }

    public Object read(CharacterIterator it) 
    {
        return read(it, NEXT);
    }

    public Object read(String string) 
    {
        return read(new StringCharacterIterator(string), FIRST);
    }

    private Object read() 
    {
        skipWhiteSpace();
        char ch = c;
        next();
        switch (ch) 
        {
            case '"': token = readString(); break;
            case '[': token = readArray(); break;
            case ']': token = ARRAY_END; break;
            case ',': token = COMMA; break;
            case '{': token = readObject(); break;
            case '}': token = OBJECT_END; break;
            case ':': token = COLON; break;
            default: {
                throw new DataException( "Unexpected character '" + ch + "'" );
            }
        }
        return token;
    }
    
    private Map readObject() 
    {
        Map ret = new LinkedHashMap();
        read();
        while (true) {
            if (token == OBJECT_END) {
                return ret;
            }
            if (!(token instanceof String)) {
                throw new DataException("Missing/illegal object key");
            }
            String key = (String) token;
            if (read() != COLON) {
                throw new DataException("Missing colon in JSON object");
            }
            Object value = read();
            ret.put(key, value);
            read();
            if (token == COMMA) {
                read();
            }
            else if (token != OBJECT_END) {
                throw new DataException("Unexpected token " + token);
            }
        }
    }

    private List readArray() 
    {
        List ret = new ArrayList();
        Object value = read();
        while (token != ARRAY_END) 
        {
            ret.add(value);
            if (read() == COMMA) 
                value = read();
            else if (token != ARRAY_END)
                throw new DataException("Unexpected token in array " + token);
        }
        return ret;
    }

    private String readString() 
    {
        buf.setLength(0);
        while (c != '"') 
        {
            if (c == '\\') 
            {
                next();
                if (c == 'u') 
                {
                    add(unicode());
                } 
                else 
                {
                    Object value = escapes.get(new Character(c));
                    if (value != null) 
                    {
                        add(((Character) value).charValue());
                    }
                }
            } 
            else 
            {
                add();
            }
        }
        next();
        return buf.toString();
    }

    private void add(char cc) 
    {
        buf.append(cc);
        next();
    }

    private void add() 
    {
        add(c);
    }

    private char unicode() 
    {
        int value = 0;
        for (int i = 0; i < 4; ++i) 
        {
            switch (next()) 
            {
                case '0': case '1': case '2': case '3': case '4': 
                case '5': case '6': case '7': case '8': case '9':
                    value = (value << 4) + c - '0';
                    break;
                case 'a': case 'b': case 'c': case 'd': case 'e': case 'f':
                    value = (value << 4) + (c - 'a') + 10;
                    break;
                case 'A': case 'B': case 'C': case 'D': case 'E': case 'F':
                    value = (value << 4) + (c - 'A') + 10;
                    break;
            }
        }
        return (char) value;
    }

    /**
     * Named object.
     */
    private static class Token {
        private final String name;
        Token(String name) {
            this.name = name;
        }
        public String toString() {
            return this.name;
        }
    }
}
