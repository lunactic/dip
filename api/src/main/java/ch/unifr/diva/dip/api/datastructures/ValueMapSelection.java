package ch.unifr.diva.dip.api.datastructures;

import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Holds a map of values and a key pointing to/selecting one. Values, just typed
 * as Objects, must be un-/marshallable with JAXB.
 */
@XmlRootElement(name = "value-map-selection")
@XmlAccessorType(XmlAccessType.NONE)
public class ValueMapSelection extends ValueMap {

	@XmlAttribute(name = "selected")
	public String selection;

	@SuppressWarnings("unused")
	public ValueMapSelection() {
		this(new HashMap<>(), "");
	}

	/**
	 * Creates a new value map selection containing the given objects.
	 *
	 * @param map the objects of the value map.
	 * @param selection the key of the selected object.
	 */
	public ValueMapSelection(Map<String, Object> map, String selection) {
		super(map);
		this.selection = selection;
	}

	/**
	 * Returns the currently selected object.
	 *
	 * @return the currently selected object.
	 */
	public Object getSelectedValue() {
		return this.map.get(this.selection);
	}

	/**
	 * Returns the key of the currently selected map entry.
	 *
	 * @return the key of the currently selected map entry.
	 */
	public String getSelectedKey() {
		return this.selection;
	}

	/**
	 * Selects a different map entry.
	 *
	 * @param key key of the map entry to be selected.
	 */
	public void setSelection(String key) {
		if (this.map.containsKey(key)) {
			this.selection = key;
		}
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName()
				+ "@"
				+ Integer.toHexString(hashCode())
				+ "{"
				+ "selected=" + selection + "; "
				+ mapToString()
				+ "}";
	}

	@Override
	public int hashCode() {
		int hash = super.hashCode();
		hash = 31 * hash + selection.hashCode();
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final ValueMapSelection other = (ValueMapSelection) obj;
		if (!this.selection.equals(other.selection)) {
			return false;
		}
		if (!this.map.equals(other.map)) {
			return false;
		}
		return true;
	}

}
