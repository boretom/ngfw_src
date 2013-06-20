/*
 * $Id$
 */
package com.untangle.uvm.node;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONObject;
import org.json.JSONString;

import com.untangle.uvm.node.NodeSettings;

/**
 * The immutable properties of a Node
 */
@SuppressWarnings("serial")
public class NodeProperties implements Serializable, JSONString
{
    private String name = null;
    private String displayName = null;
    private String className = null;
    private String nodeBase = null;

    public enum Type {
        FILTER,
        SERVICE,
        UNKNOWN
    }
    private Type type;
    
    private Boolean hasPowerButton = true;
    private Boolean autoStart = true;

    private List<String> parents = new LinkedList<String>();

    private Integer viewPosition = null;

    public NodeProperties() {}
    
    /**
     * Internal name of the node.
     *
     * @return the node's name.
     */
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    /**
     * Name of the main node Class.
     *
     * @return node class name.
     */
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    /**
     * The parent node, usually a casing.
     *
     * @return the parent node, null if node has no parent.
     */
    public List<String> getParents() { return parents; }
    public void setParents(List<String> parents) { this.parents = parents; }

    /**
     * The name of the node, for display purposes.
     *
     * @return display name.
     */
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    
    /**
     * The nodeBase is the name of the base node. For example
     * clam-node's nodeBase is untangle-base-virus.
     *
     * @return the nodeBase, null if node does not have a base.
     */
    public String getNodeBase() { return nodeBase; }
    public void setNodeBase(String nodeBase) { this.nodeBase = nodeBase; }

    /**
     * The type is the type of node
     *
     * @return the type, null if node does not have a type.
     */
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    /**
     * The view position in the rack
     *
     * @return the type, null if node does not have a type.
     */
    public Integer getViewPosition() { return viewPosition; }
    public void setViewPosition(Integer viewPosition) { this.viewPosition = viewPosition; }
    
    /**
     * True if this node can be turned on and off.  False, otherwise.
     */
    public Boolean getHasPowerButton() { return hasPowerButton; }
    public void setHasPowerButton(Boolean hasPowerButton) { this.hasPowerButton = hasPowerButton; }

    /**
     * True if this node should be started automatically.
     */
    public Boolean getAutoStart() { return autoStart; }
    public void setAutoStart(Boolean autoStart) { this.autoStart = autoStart; }

    // Object methods ---------------------------------------------------------

    /**
     * Equality based.
     *
     * @param o the object to compare to.
     * @return true if equal.
     */
    public boolean equals(Object o)
    {
        if (!(o instanceof NodeProperties)) {
            return false;
        }

        NodeProperties td = (NodeProperties)o;

        return getClassName().equals(td.getClassName());
    }

    public String toString()
    {
        return toJSONString();
    }

    public String toJSONString()
    {
        JSONObject jO = new JSONObject(this);
        return jO.toString();
    }
    
}
