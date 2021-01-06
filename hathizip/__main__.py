"""Bootstrap the hathizip cli"""

import sys

from hathizip import cli


def main():
    """Main entry point."""
    if len(sys.argv) > 1 and sys.argv[1] == "--pytest":
        import pytest  # type: ignore
        sys.exit(pytest.main(sys.argv[2:]))
    else:
        cli.main()


if __name__ == '__main__':
    main()
