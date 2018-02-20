from setuptools import setup
import os


setup(
    packages=['hathizip'],
    test_suite="tests",
    setup_requires=['pytest-runner'],
    tests_require=['pytest'],
    entry_points={
        "console_scripts": [
            "hathizip = hathizip.__main__:main"
        ]
    },
)
