"""Regression checks for inherited members; no game or javap process needed."""
import unittest
from mixcheck import Jar


class HierarchyTest(unittest.TestCase):
    def jar(self, members):
        jar = Jar(None)
        jar.members = lambda name: members.get(name, "")
        return jar

    def test_generic_bound_does_not_hide_real_superclass(self):
        jar = self.jar({
            "net.minecraft.Minecraft": "class Minecraft extends net.minecraft.Reentrant<java.lang.Runnable> {",
            "net.minecraft.Reentrant": "class Reentrant<R extends java.lang.Runnable> extends net.minecraft.Loop<R> {",
            "net.minecraft.Loop": "class Loop<R extends java.lang.Runnable> {\n  protected void runAllTasks();\n}",
        })
        self.assertTrue(jar.has_member("net.minecraft.Minecraft", "runAllTasks"))

    def test_inherited_default_interface_method(self):
        jar = self.jar({
            "net.minecraft.Level": "class Level implements net.minecraft.LevelAccessor, java.lang.AutoCloseable {",
            "net.minecraft.LevelAccessor": "interface LevelAccessor extends net.minecraft.Blocks, net.minecraft.Entities {",
            "net.minecraft.Entities": "interface Entities {\n  public default java.util.List getEntityCollisions();\n}",
        })
        self.assertTrue(jar.has_member("net.minecraft.Level", "getEntityCollisions"))
        self.assertFalse(jar.has_member("net.minecraft.Level", "missingMethod"))

    def test_cycles_and_shared_ancestors_are_visited_once(self):
        jar = self.jar({
            "net.minecraft.A": "interface A extends net.minecraft.B, net.minecraft.C {",
            "net.minecraft.B": "interface B extends net.minecraft.C {",
            "net.minecraft.C": "interface C extends net.minecraft.A {",
        })
        self.assertEqual(["net.minecraft.A", "net.minecraft.B", "net.minecraft.C"], jar.hierarchy("net.minecraft.A"))


if __name__ == "__main__":
    unittest.main()
