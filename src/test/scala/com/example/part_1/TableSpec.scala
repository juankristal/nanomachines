package com.example.part_1

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

import scala.language.postfixOps

class TableSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "Table actor" must {
    "reply message for a client that sits on a table" in {
      val probe = createTestProbe[Table.Answer]()
      val tableActor = spawn(Table("Mesa 1"))

      tableActor ! Table.SitClient(42, "Juani", probe.ref)
      val response = probe.receiveMessage()
      response.msg shouldBe "Request id: 42\nClient Juani sat\n"
    }
  }
}
