package es.us.isa.httpmutator.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Class to manage insertion, deletion and replacement of elements in JSON (objects
 * and arrays).
 *
 * @author Alberto Martin-Lopez
 */
public class JsonManager {

    /**
     * Replace element in an object or array. NOTE: In the case of an object, if the
     * property didn't exist, it creates it (unlike with arrays, where the element is
     * always replaced).
     *
     * @param jsonNode The object or array where to replace the element
     * @param element The value of the element after being replaced
     * @param propertyName The name of the property to replace. Must be null if jsonNode
     *                     is an ArrayNode
     * @param index The index position in the array where to replace the element. Must
     *              be null if jsonNode is an ObjectNode
     */
    public static void insertElement(JsonNode jsonNode, Object element, String propertyName, Integer index) {
        boolean isObj = index==null; // If index==null, jsonNode is an object, otherwise it is an array
        if (element instanceof String) {
            if (isObj) ((ObjectNode)jsonNode).put(propertyName, (String)element);
            else ((ArrayNode)jsonNode).set(index, new TextNode((String)element));
        } else if (element instanceof Long) {
            if (isObj) ((ObjectNode)jsonNode).put(propertyName, (Long)element);
            else ((ArrayNode)jsonNode).set(index, new LongNode((Long)element));
        } else if (element instanceof Double) {
            if (isObj) ((ObjectNode)jsonNode).put(propertyName, (Double)element);
            else ((ArrayNode)jsonNode).set(index, new DoubleNode((Double)element));
        } else if (element instanceof Boolean) {
            if (isObj) ((ObjectNode)jsonNode).put(propertyName, (Boolean)element);
            else ((ArrayNode)jsonNode).set(index, (Boolean)element ? BooleanNode.TRUE : BooleanNode.FALSE);
        } else if (element instanceof NullNode) {
            if (isObj) ((ObjectNode)jsonNode).putNull(propertyName);
            else ((ArrayNode)jsonNode).set(index, null);
        } else if (element instanceof ObjectNode) {
            if (isObj) ((ObjectNode)jsonNode).replace(propertyName, (ObjectNode)element);
            else ((ArrayNode)jsonNode).set(index, (ObjectNode)element);
        } else if (element instanceof ArrayNode) {
            if (isObj) ((ObjectNode)jsonNode).replace(propertyName, (ArrayNode)element);
            else ((ArrayNode)jsonNode).set(index, (ArrayNode)element);
        } else {
            throw new IllegalArgumentException("The element to insert must be a string, int, float, boolean, " +
                    "object, array or null value.");
        }
    }

    /**
     * Given a JsonNode (e.g., TextNode, NumberNode, etc.), returns the value
     * of such node as a basic type (e.g., String, Float, etc.)
     * @param jsonNode The object or array whose value will be returned as a basic type
     * @return The value of the element as a basic type
     */
    public static Object getNodeElement(JsonNode jsonNode) {
        if (jsonNode.isTextual())
            return jsonNode.asText();
        else if (jsonNode.isIntegralNumber())
            return jsonNode.asLong();
        else if (jsonNode.isFloatingPointNumber())
            return jsonNode.asDouble();
        else if (jsonNode.isBoolean())
            return jsonNode.asBoolean();
        else if (jsonNode.isNull())
            return null;
        else if (jsonNode.isContainerNode())
            return jsonNode;
        else
            throw new IllegalArgumentException("Element not supported. It must be a string, int, float, boolean, " +
                    "object, array or null value.");
    }
}
