package BIOfid.Utility;

import java.util.HashMap;

public class CountMap<T> extends HashMap<T, Integer> {
	
	/**
	 * Add the key to the CountMap. This will not affect keys already added.
	 *
	 * @param key
	 */
	public void add(T key) {
		this.put(key, this.getOrDefault(key, 0));
	}
	
	/**
	 * Increase the count for this key. If the key does not exist, it will be added.
	 *
	 * @param key
	 */
	public void inc(T key) {
		this.put(key, this.getOrDefault(key, 0) + 1);
	}
	
	/**
	 * Get the count for this key, or 0 if it does not exist.
	 *
	 * @param key
	 * @return
	 */
	public Integer get(Object key) {
		return this.getOrDefault(key, 0);
	}
	
}
