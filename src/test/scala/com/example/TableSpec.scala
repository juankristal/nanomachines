package com.example

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class TableSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import Table._

  "Table actor" must {

    "reply with nothing if no client did sit yet" in {
      val probe = createTestProbe[ClientCommand]()
      val tableActor = spawn(Table("Mesa 1"))

      tableActor ! Table.GetClients(requestId = 42, probe.ref)
      val response = probe.receiveMessage()
      response.requestId should ===(42)
      response.value should ===(None)
    }

    "reply with latest client that sat" in {
      val recordProbe = createTestProbe[ClientCommand]()
      val readProbe = createTestProbe[ClientCommand]()
      val deviceActor = spawn(Table("Mesa 2"))

      deviceActor ! Table.SitClient(requestId = 1, "Juani", recordProbe.ref)
      recordProbe.expectMessage(Table.ClientSat(requestId = 1))

      deviceActor ! Table.GetClients(requestId = 2, readProbe.ref)
      val response = readProbe.receiveMessage()
      response.requestId should ===(2)
      response.value should ===(Some("Juani"))

      deviceActor ! Table.SitClient(requestId = 3, "Cristo", recordProbe.ref)
      recordProbe.expectMessage(Table.ClientSat(requestId = 3))

      deviceActor ! Table.GetClients(requestId = 4, readProbe.ref)
      val anotherResponse = readProbe.receiveMessage()
      anotherResponse.requestId should ===(4)
      anotherResponse.value should ===(Some("Cristo"))
    }
  }
}
