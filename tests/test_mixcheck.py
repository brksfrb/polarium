import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("mixcheck", Path(__file__).resolve().parents[1] / "mixcheck.py")
mixcheck = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mixcheck)


class HierarchyTests(unittest.TestCase):
    def test_generic_bound_is_not_the_superclass(self):
        self.assertEqual([
            "net.minecraft.Parent", "net.minecraft.First", "net.minecraft.Second"],
            mixcheck.direct_parents("public class net.minecraft.Child<R extends java.lang.Runnable> "
                                    "extends net.minecraft.Parent<R> implements net.minecraft.First,net.minecraft.Second "))

    def test_default_methods_and_generic_superclasses_are_valid_targets(self):
        jar = mixcheck.Jar("unused")
        headers = {
            "net.minecraft.Child": "public class net.minecraft.Child<R extends java.lang.Runnable> extends net.minecraft.Parent<R> implements net.minecraft.Access {",
            "net.minecraft.Parent": "public class net.minecraft.Parent<R> extends net.minecraft.Loop<R> {",
            "net.minecraft.Loop": "public class net.minecraft.Loop<R> {\n  public void runAllTasks();",
            "net.minecraft.Access": "public interface net.minecraft.Access extends net.minecraft.Collisions {",
            "net.minecraft.Collisions": "public interface net.minecraft.Collisions {\n  public default void getEntityCollisions();",
        }
        jar.members = lambda cls: headers.get(cls, "")
        self.assertTrue(jar.has_member("net.minecraft.Child", "runAllTasks"))
        self.assertTrue(jar.has_member("net.minecraft.Child", "getEntityCollisions"))
        self.assertFalse(jar.has_member("net.minecraft.Child", "missing"))


if __name__ == "__main__":
    unittest.main()
