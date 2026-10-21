"""
Automated Test Suite: State Machine & Presentation Architecture
JARVIS Dynamic Island (Cross-Platform Glanceable Surface)
"""

import unittest

class JarvisStateMachine:
    def __init__(self, open_delay_ms: float = 70.0, close_delay_ms: float = 200.0):
        self.state = "closed"
        self.open_delay_ms = open_delay_ms
        self.close_delay_ms = close_delay_ms
        self.hover_start_time = None
        self.leave_start_time = None
        self.peek_until_ms = None
        self.is_pinned = False
        self.show_volume_hud = False
        self.volume_hud_until = 0
        self.avoid_fullscreen = True
        self.is_foreground_fullscreen = False

    def on_pointer_enter(self, current_time_ms: float, expand_on_hover: bool = True):
        self.leave_start_time = None
        if self.state in ("expanded", "suppressed"):
            return
        self.state = "peek"
        self.hover_start_time = current_time_ms

    def on_pointer_tick(self, current_time_ms: float, expand_on_hover: bool = True):
        if self.state == "suppressed":
            return

        if self.hover_start_time is not None and expand_on_hover:
            if (current_time_ms - self.hover_start_time) >= self.open_delay_ms:
                self.state = "expanded"
                self.hover_start_time = None
                self.peek_until_ms = None

        if self.peek_until_ms is not None:
            if current_time_ms >= self.peek_until_ms and self.state == "peek":
                self.state = "closed"
                self.peek_until_ms = None

        if self.leave_start_time is not None and not self.is_pinned:
            if (current_time_ms - self.leave_start_time) >= self.close_delay_ms:
                self.state = "closed"
                self.leave_start_time = None
                self.peek_until_ms = None

    def on_pointer_leave(self, current_time_ms: float):
        self.hover_start_time = None
        if self.state in ("expanded", "peek") and not self.is_pinned:
            self.leave_start_time = current_time_ms

    def on_click(self):
        self.hover_start_time = None
        self.leave_start_time = None
        self.peek_until_ms = None
        if self.state != "suppressed":
            self.state = "expanded"

    def trigger_peek(self, current_time_ms: float, duration_ms: float = 2600.0):
        if self.state == "closed" and not self.is_foreground_fullscreen:
            self.state = "peek"
            self.peek_until_ms = current_time_ms + duration_ms

    def set_foreground_fullscreen(self, is_fs: bool):
        self.is_foreground_fullscreen = is_fs
        if is_fs and self.avoid_fullscreen:
            self.state = "suppressed"
        elif not is_fs and self.state == "suppressed":
            self.state = "closed"

    def toggle_pin(self):
        self.is_pinned = not self.is_pinned
        if self.is_pinned:
            self.leave_start_time = None

    def on_escape_or_outside_click(self):
        self.state = "closed"
        self.is_pinned = False
        self.hover_start_time = None
        self.leave_start_time = None
        self.peek_until_ms = None

    def trigger_volume_hud(self, current_time_ms: float, duration_ms: float = 2200.0):
        self.show_volume_hud = True
        self.volume_hud_until = current_time_ms + duration_ms

    def is_volume_hud_active(self, current_time_ms: float) -> bool:
        return self.show_volume_hud and current_time_ms < self.volume_hud_until

    def get_dimensions(self):
        if self.state == "suppressed":
            return {"width": 210, "height": 0, "radius": 0}
        elif self.state == "peek":
            return {"width": 320, "height": 44, "radius": 22}
        elif self.state == "expanded":
            return {"width": 420, "height": 190, "radius": 24}
        else: # closed / compact
            return {"width": 210, "height": 36, "radius": 18}


class TestJarvisStateMachine(unittest.TestCase):
    def test_casual_sweep_enters_peek_then_reverts(self):
        sm = JarvisStateMachine(open_delay_ms=70.0, close_delay_ms=140.0)
        sm.on_pointer_enter(0.0)
        self.assertEqual(sm.state, "peek", "Immediate hover enters glanceable peek")
        sm.on_pointer_tick(30.0)
        self.assertEqual(sm.state, "peek", "Sub-70ms dwell remains in peek")
        sm.on_pointer_leave(35.0)
        sm.on_pointer_tick(200.0)
        self.assertEqual(sm.state, "closed", "Leaving reverts to closed")

    def test_hover_dwell_expands_island(self):
        sm = JarvisStateMachine(open_delay_ms=70.0)
        sm.on_pointer_enter(0.0)
        sm.on_pointer_tick(75.0)
        self.assertEqual(sm.state, "expanded", "Sustained hover must expand the island")

    def test_transient_peek_glance_lifecycle(self):
        sm = JarvisStateMachine()
        sm.trigger_peek(1000.0, duration_ms=2600.0)
        self.assertEqual(sm.state, "peek")
        dim = sm.get_dimensions()
        self.assertEqual(dim["height"], 44)
        self.assertEqual(dim["width"], 320)
        # 2s in: still peek
        sm.on_pointer_tick(3000.0)
        self.assertEqual(sm.state, "peek")
        # 3.7s in: expired, returned to closed
        sm.on_pointer_tick(3700.0)
        self.assertEqual(sm.state, "closed")

    def test_click_during_peek_expands_immediately(self):
        sm = JarvisStateMachine()
        sm.trigger_peek(1000.0, duration_ms=2600.0)
        self.assertEqual(sm.state, "peek")
        sm.on_click()
        self.assertEqual(sm.state, "expanded")

    def test_fullscreen_suppression_and_recovery(self):
        sm = JarvisStateMachine()
        sm.state = "expanded"
        sm.set_foreground_fullscreen(True)
        self.assertEqual(sm.state, "suppressed")
        dim = sm.get_dimensions()
        self.assertEqual(dim["height"], 0, "Suppressed state must have 0 height to prevent blocking")
        # Exiting fullscreen recovers to closed
        sm.set_foreground_fullscreen(False)
        self.assertEqual(sm.state, "closed")

    def test_mouse_out_collapses_after_grace_period(self):
        sm = JarvisStateMachine(close_delay_ms=200.0)
        sm.state = "expanded"
        sm.on_pointer_leave(100.0)
        sm.on_pointer_tick(250.0)
        self.assertEqual(sm.state, "expanded", "Must remain expanded within 200ms grace window")
        sm.on_pointer_tick(310.0)
        self.assertEqual(sm.state, "closed", "Must collapse after grace window expires")

    def test_reenter_cancels_collapse(self):
        sm = JarvisStateMachine(close_delay_ms=200.0)
        sm.state = "expanded"
        sm.on_pointer_leave(100.0)
        sm.on_pointer_enter(180.0)
        sm.on_pointer_tick(350.0)
        self.assertEqual(sm.state, "expanded", "Re-entering must cancel pending collapse")

    def test_pinned_island_does_not_collapse_on_leave(self):
        sm = JarvisStateMachine(close_delay_ms=200.0)
        sm.state = "expanded"
        sm.toggle_pin()
        self.assertTrue(sm.is_pinned)
        sm.on_pointer_leave(100.0)
        sm.on_pointer_tick(500.0)
        self.assertEqual(sm.state, "expanded", "Pinned island must never collapse on mouse leave")

    def test_escape_collapses_and_unpins(self):
        sm = JarvisStateMachine()
        sm.state = "expanded"
        sm.toggle_pin()
        sm.on_escape_or_outside_click()
        self.assertEqual(sm.state, "closed")
        self.assertFalse(sm.is_pinned)

    def test_volume_hud_lifecycle(self):
        sm = JarvisStateMachine()
        sm.trigger_volume_hud(1000.0, duration_ms=2200.0)
        self.assertTrue(sm.is_volume_hud_active(1500.0))
        self.assertTrue(sm.is_volume_hud_active(3199.0))
        self.assertFalse(sm.is_volume_hud_active(3201.0), "Volume HUD must auto-dismiss after duration")

if __name__ == "__main__":
    unittest.main()
