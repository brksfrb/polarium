package com.arcticlauncher.polonium;

/**
 * -Dpolonium.debugTimeline=true: where the render thread's frame goes, by
 * Polonium's steps, as milliseconds per frame (waiting for helpers
 * included, which a sampling profiler can't see). Logged every 10 s.
 */
public final class Timeline {
	public static final boolean ON = Boolean.getBoolean("polonium.debugTimeline");

	public enum Step {
		FRAME, RUN_TICK, RENDER_FRAME, GUI_UPDATE, LEVEL_UPDATE, CAMERA_UPDATE, PENDING_TASKS, RUN_TASKS, TICK, PICK, ACQUIRE, BLIT, SUBMIT, PRESENT, TICK_ENTITIES, PARALLEL_TICKS, EXTRACT, EXTRACT_ENTITIES, EXTRACT_JOIN, RENDER, SUBMIT_ENTITIES, CROWD_BULK, CROWD_END,
		PREPARE_FRAME, PREP_MODELS, PREP_ITEMS, PREP_TAGS, CROWD_LAYOUT, CROWD_COMPUTE, CROWD_UPLOAD, CROWD_DRAWS
	}

	private static final long[] TOTAL = new long[Step.values().length];
	private static final long[] STARTED = new long[Step.values().length];
	private static long frames;
	private static long lastReport = System.nanoTime();

	private Timeline() {}

	public static void start(Step step) {
		if (ON) {
			STARTED[step.ordinal()] = System.nanoTime();
		}
	}

	public static void end(Step step) {
		if (ON && STARTED[step.ordinal()] != 0) {
			TOTAL[step.ordinal()] += System.nanoTime() - STARTED[step.ordinal()];
			STARTED[step.ordinal()] = 0;
		}
	}

	/** A frame ended (render thread). */
	public static void frame() {
		if (!ON) {
			return;
		}
		end(Step.FRAME);
		frames++;
		long now = System.nanoTime();
		if (now - lastReport > 10_000_000_000L && frames > 0) {
			StringBuilder line = new StringBuilder(String.format(java.util.Locale.ROOT, "Polonium timeline (%.0f FPS):", frames / ((now - lastReport) / 1e9)));
			for (Step step : Step.values()) {
				line.append(String.format(java.util.Locale.ROOT, " %s %.2f;", step.name().toLowerCase(java.util.Locale.ROOT), TOTAL[step.ordinal()] / 1e6 / frames));
			}
			//#if MC >= 26.2
			line.append(" states: ").append(LightStates.counts());
			line.append(String.format(java.util.Locale.ROOT, " visible: short %d, long %d, untrusted %d", ParallelExtract.VISIBLE_CALLS[0],
					ParallelExtract.VISIBLE_CALLS[1], ParallelExtract.VISIBLE_CALLS[2]));
			java.util.Arrays.fill(ParallelExtract.VISIBLE_CALLS, 0);
			line.append(String.format(java.util.Locale.ROOT, " helpers (thread-ms per frame): extract jobs %.2f, visible %.2f, made %.2f;",
					ParallelExtract.JOB_NANOS.sumThenReset() / 1e6 / frames, ParallelExtract.VISIBLE_NANOS.sumThenReset() / 1e6 / frames,
					ParallelExtract.MADE_NANOS.sumThenReset() / 1e6 / frames));
			//#endif
			org.slf4j.LoggerFactory.getLogger("Polonium").info(line.toString());
			java.util.Arrays.fill(TOTAL, 0);
			frames = 0;
			lastReport = now;
		}
		start(Step.FRAME);
	}
}
