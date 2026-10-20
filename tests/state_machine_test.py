"""
Automated Test Suite: State Machine & Dwell Filtering
JARVIS Dynamic Island
"""

import unittest

class JarvisStateMachine:
    def __init__(self, open_delay_ms: float = 70.0, close_delay_ms: float = 200.0):
        self.state = "closed"
        self.open_delay_ms = open_delay_ms
        self.close_delay_ms = close_delay_ms
        self.hover_start_time = None
        self.leave_start_time = None
        self.is_pinned = False
        self.show_volume_hud = False
        self.volume_hud_until = 0

    def on_pointer_enter(self, current_time_ms: float):
        self.leave_start_time = None
        if self.state == "expanded":
            return
        self.hover_start_time = current_time_ms

    def on_pointer_tick(self, current_time_ms: float):
        if self.hover_start_time is not None:
            if (current_time_ms - self.hover_start_time) >= self.open_delay_ms:
                self.state = "expanded"
                self.hover_start_time = None

        if self.leave_start_time is not None and not self.is_pinned:
            if (current_time_ms - self.leave_start_time) >= self.close_delay_ms:
                self.state = "closed"
                self.leave_start_time = None

    def on_pointer_leave(self, current_time_ms: float):
        self.hover_start_time = None
        if self.state == "expanded" and not self.is_pinned:
            self.leave_start_time = current_time_ms

    def on_click(self):
        self.hover_start_time = None
        self.leave_start_time = None
        self.state = "expanded"

    def toggle_pin(self):
        self.is_pinned = not self.is_pinned
        if self.is_pinned:
            self.leave_start_time = None

    def on_escape_or_outside_click(self):
        self.state = "closed"
        self.is_pinned = False
        self.hover_start_time = None
        self.leave_start_time = None

    def trigger_volume_hud(self, current_time_ms: float, duration_ms: float = 2200.0):
        self.show_volume_hud = True
        self.volume_hud_until = current_time_ms + duration_ms

    def is_volume_hud_active(self, current_time_ms: float) -> bool:
        return self.show_volume_hud and current_time_ms < self.volume_hud_until


class TestJarvisStateMachine(unittest.TestCase):
    def test_casual_sweep_ignored(self):
        sm = JarvisStateMachine(open_delay_ms=70.0)
        sm.on_pointer_enter(0.0)
        sm.on_pointer_tick(30.0)
        self.assertEqual(sm.state, "closed", "Quick sweep under 70ms dwell should remain closed")
        sm.on_pointer_leave(35.0)
        self.assertEqual(sm.state, "closed")

    def test_hover_dwell_expands_island(self):
        sm = JarvisStateMachine(open_delay_ms=70.0)
        sm.on_pointer_enter(0.0)
        sm.on_pointer_tick(75.0)
        self.assertEqual(sm.state, "expanded", "Sustained hover must expand the island")

    def test_mouse_out_collapses_after_grace_period(self):
        sm = JarvisStateMachine(close_delay_ms=200.0)
        sm.state = "expanded"
        sm.on_pointer_leave(100.0)
        # Inside grace period: still expanded
        sm.on_pointer_tick(250.0)
        self.assertEqual(sm.state, "expanded", "Must remain expanded within 200ms grace window")
        # After grace period: collapsed
        sm.on_pointer_tick(310.0)
        self.assertEqual(sm.state, "closed", "Must collapse after grace window expires")

    def test_reenter_cancels_collapse(self):
        sm = JarvisStateMachine(close_delay_ms=200.0)
        sm.state = "expanded"
        sm.on_pointer_leave(100.0)
        # User re-enters at 180ms (before 200ms expires)
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

    def test_click_triggers_immediate_expansion(self):
        sm = JarvisStateMachine()
        self.assertEqual(sm.state, "closed")
        sm.on_click()
        self.assertEqual(sm.state, "expanded")

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
