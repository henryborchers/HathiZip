from setuptools import setup
import os

metadata = dict()
metadata_file = os.path.join(os.path.abspath(os.path.dirname(__file__)), 'hathizip', '__version__.py')
with open(metadata_file, 'r', encoding='utf-8') as f:
    exec(f.read(), metadata)

setup(
    name=metadata["__title__"],
    version=metadata["__version__"],
    packages=['hathizip'],
    url=metadata["__url__"],
    license='University of Illinois/NCSA Open Source License',
    author=metadata["__author__"],
    author_email=metadata["__author_email__"],
    description=metadata["__description__"],
    test_suite="tests",
    setup_requires=['pytest-runner'],
    tests_require=['pytest'],
    entry_points={
        "console_scripts": [
            "hathizip = hathizip.__main__:main"
        ]
    },
)
