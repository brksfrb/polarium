//#if MC >= 26.2
package com.arcticlauncher.polarium.gpu;

/**
 * A player's render state, checked against its recipe while it was made (on
 * the helper threads, see {@link Crowd#precheck}): the recipe it still
 * matches, and for which frame. Added to AvatarRenderState by a mixin.
 */
public interface CrowdChecked {
	Object polarium$checked();

	long polarium$checkedFrame();

	void polarium$checked(Object recipe, long frame);

	/** Whether any layer left to the game has something to draw for it (worked out with the check). */
	boolean polarium$liveLayers();

	void polarium$liveLayers(boolean live);

	/** The frame the crowd path took it in bulk (see Crowd#bulkSubmit): the game's submit skips it. */
	long polarium$taken();

	void polarium$taken(long frame);

	/**
	 * Whether it can be taken in bulk, worked out with the check: the frame
	 * if so, minus the frame if not (anything else: not worked out).
	 */
	long polarium$takeable();

	void polarium$takeable(long frame);
}
//#endif
