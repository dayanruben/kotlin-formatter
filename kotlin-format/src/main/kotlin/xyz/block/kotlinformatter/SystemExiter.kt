package xyz.block.kotlinformatter

interface SystemExiter {
  fun exit(status: Int)
}

class RealSystemExiter : SystemExiter {
  override fun exit(status: Int) {
    System.exit(status)
  }
}